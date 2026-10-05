/**
 * Bluetooth Classic transport: opens an RFCOMM socket to the OBEX Object Push service (UUID
 * `00001105-...`) on a paired device and runs the OBEX CONNECT/PUT/DISCONNECT exchange defined
 * in [ObexProtocol].
 *
 * Why OBEX Object Push specifically: it is the one Bluetooth file-transfer profile that legacy
 * "dumbphones" - this project was built and tested against an Alcatel 3080A - are able to
 * receive without any companion app, since it has been part of Bluetooth Classic since long
 * before BLE and modern transfer profiles existed.
 *
 * Reliability details worth knowing when touching this file:
 * - Blocking socket I/O (`connect()`, `InputStream.read()`) does not respond to coroutine
 *   cancellation. A watchdog coroutine force-closes the socket after [timeoutMillis], which is
 *   what turns a stuck read into a catchable [ObexException] instead of hanging the worker
 *   forever.
 * - Success is determined by the PUT FINAL response, not by a clean DISCONNECT. If the file was
 *   already accepted by the receiver but the teardown afterwards fails or times out, that is
 *   logged and swallowed rather than treated as a failed transfer - otherwise the caller would
 *   retry and the receiver would get the same file twice.
 *
 * Limitations: transfers are strictly sequential (no pipelining), and there is no support for
 * OBEX profiles other than Object Push (no OPP business-card exchange, no MAP/PBAP, etc.) -
 * this project only ever needed to push a text file.
 */
package app.notificationbridge.bluetooth
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import app.notificationbridge.model.PairedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import kotlin.math.min
class ObexException(message:String,val responseCode:Int?=null,cause:Throwable?=null):IOException(message,cause)
class ObexObjectPushClient(private val context:Context,private val log:(String)->Unit){
 companion object{
  val OPP_UUID:UUID=UUID.fromString("00001105-0000-1000-8000-00805F9B34FB")
  const val DEFAULT_TIMEOUT_MS:Long=15000L
 }
 private val adapter get()=context.getSystemService(BluetoothManager::class.java)?.adapter
 @SuppressLint("MissingPermission") fun pairedDevices()=adapter?.bondedDevices.orEmpty().map{PairedDevice(it.name?:"(Sin nombre)",it.address)}.sortedBy{it.name.lowercase()}
 @SuppressLint("MissingPermission") suspend fun push(address:String,fileName:String,mimeType:String,content:ByteArray,timeoutMillis:Long=DEFAULT_TIMEOUT_MS)=withContext(Dispatchers.IO){
  val a=adapter?:throw ObexException("Bluetooth no disponible")
  if(!a.isEnabled)throw ObexException("Bluetooth desactivado")
  val d=a.getRemoteDevice(address)
  if(d.bondState!=BluetoothDevice.BOND_BONDED)throw ObexException("Dispositivo no emparejado")
  log("Bluetooth device found: ${d.address}")
  log("OBEX object name: $fileName")
  log("OBEX object size: ${content.size} bytes")
  a.cancelDiscovery()
  val s=d.createRfcommSocketToServiceRecord(OPP_UUID)
  var timedOut=false
  // Watchdog: blocking socket I/O below doesn't respond to coroutine cancellation,
  // so we force-close the socket after timeoutMillis to unblock any stuck connect()/read().
  val watchdog=launch{
   delay(timeoutMillis)
   timedOut=true
   log("OBEX transfer timed out after ${timeoutMillis}ms, forcing socket close")
   runCatching{s.close()}
  }
  try{
   log("OBEX service lookup requested: $OPP_UUID")
   s.connect()
   log("RFCOMM connection established")
   val i=s.inputStream
   val o=s.outputStream
   ObexProtocol.write(o,ObexProtocol.connectPacket())
   log("OBEX CONNECT sent")
   val cr=ObexProtocol.readResponse(i)
   log("OBEX CONNECT response received: 0x%02X %s".format(cr.code,cr.label))
   if(cr.code!=ObexProtocol.SUCCESS)throw ObexException("CONNECT rejected",cr.code)
   val max=ObexProtocol.serverMaxPacket(cr).coerceIn(255,65535)
   val id=ObexProtocol.connectionId(cr)
   sendPut(o,i,max,id,fileName,mimeType,content)
   // The file is fully delivered and acknowledged by the receiver at this point.
   // A flaky/slow teardown from here on must NOT be treated as a failed transfer:
   // doing so used to trigger a retry that resent the whole file, causing the
   // receiver to see the same notification twice even though it already arrived.
   runCatching{
    ObexProtocol.write(o,ObexProtocol.disconnectPacket(id))
    log("OBEX DISCONNECT sent")
    val dr=ObexProtocol.readResponse(i)
    log("OBEX DISCONNECT response: 0x%02X %s".format(dr.code,dr.label))
    if(dr.code!=ObexProtocol.SUCCESS)log("DISCONNECT rejected (0x%02X), ignoring - file already delivered".format(dr.code))
   }.onFailure{log("DISCONNECT teardown failed, ignoring - file already delivered: ${it.message}")}
  }catch(e:ObexException){
   throw e
  }catch(e:Exception){
   if(timedOut)throw ObexException("Bluetooth/OBEX timeout after ${timeoutMillis}ms",null,e)
   throw ObexException("Bluetooth/OBEX failure: ${e.message}",null,e)
  }finally{
   watchdog.cancel()
   try{s.close()}catch(_:Exception){}
   log("BluetoothSocket closed")
  }
 }
 private fun sendPut(o:java.io.OutputStream,i:java.io.InputStream,max:Int,id:Long?,name:String,type:String,content:ByteArray){val meta=buildList{id?.let{add(ObexProtocol.connectionHeader(it))};add(ObexProtocol.nameHeader(name));add(ObexProtocol.typeHeader(type));add(ObexProtocol.lengthHeader(content.size))};var off=0;var first=true;do{val fixed=if(first)meta.sumOf{it.size}else(if(id!=null)5 else 0);val avail=max-3-fixed-3;if(avail<=0)throw ObexException("MTU too small");val count=min(avail,content.size-off);val final=off+count>=content.size;val h=mutableListOf<ByteArray>();if(first)h.addAll(meta)else id?.let{h.add(ObexProtocol.connectionHeader(it))};h.add(ObexProtocol.bodyHeader(content.copyOfRange(off,off+count),final));ObexProtocol.write(o,ObexProtocol.packet(if(final)ObexProtocol.PUT_FINAL else ObexProtocol.PUT,*h.toTypedArray()));log("OBEX PUT${if(final)" FINAL" else ""} sent: $count bytes, file=$name");val r=ObexProtocol.readResponse(i);log("OBEX PUT response received: 0x%02X %s".format(r.code,r.label));if(r.code!=(if(final)ObexProtocol.SUCCESS else ObexProtocol.CONTINUE))throw ObexException("Unexpected PUT response",r.code);off+=count;first=false}while(off<content.size||first);log("Transfer completed: $name (${content.size} bytes)")}
}
