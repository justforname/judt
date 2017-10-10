/**
 * 
 */
package judp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import udt.UDTSession;
import udt.UDTSocket;
import udt.packets.Destination;

/**
 * @author jinyu
 *
 *服务端返回的网络接口对象
 *保存socket并检查有数据的对象
 */
public class judpSocket {
private   int bufSize=65535;
private UDTSocket socket=null;
private boolean isClose=false;
private long sendLen=0;//发送数据
private long socketID=0;//ID
private Thread closeThread;
private final int waitClose=10*1000;
private PackagetCombin pack=new PackagetCombin();
//private int readLen=0;
public int dataLen=0;
public void setRecBufferSize(int size)
{
	bufSize=size;
}
public boolean getCloseState()
{
	//底层已经关闭
	return isClose|socket.isClose();
}
public judpSocket(UDTSocket  usocket)
{
	this.socket=usocket;
	socketID=socket.getSession().getSocketID();
}

/**
 * 获取ID
 * @return
 */
public long getSocketID()
{
	return socketID;
}

/**
 * 关闭
 * 等待数据完成关闭
 */
public void close()
{
	isClose=true;
	//不能真实关闭
	if(sendLen==0)
	{
		stop();
		 System.out.println("物理关闭socket");
	}
	else
	{
		//有过发送数据则缓冲
		//SocketManager.getInstance().add(socket);
	
		if(closeThread==null)
		{
			closeThread=new Thread(new Runnable() {

				@Override
				public void run() {
					int num=0;
				while(true)
				{
					if(socket.getSender().isSenderEmpty())
					{
						stop();
						break;
					}
					else
					{
						try {
							TimeUnit.MILLISECONDS.sleep(100);
							num++;
							if(waitClose<=num*100)
							{
								stop();
								break;
							}
						} catch (InterruptedException e) {
							
							e.printStackTrace();
						}
					}
				}
					
				}
				
			});
			closeThread.setDaemon(true);
			closeThread.setName("closeThread");
		}
		if(closeThread.isAlive())
		{
			return;
		}
		else
		{
			closeThread.start();
		}
	}
}

/**
 * 立即关闭
 */
public void stop()
{
	//没有发送则可以直接关闭，不需要等待数据发送完成
	 try {
		socket.close();
		UDTSession serversession=socket.getEndpoint().removeSession(socketID);
		if(serversession!=null)
		{
			serversession.getSocket().close();
		     socket.getReceiver().stop();
		     socket.getSender().stop();
			System.out.println("物理关闭socket:"+serversession.getSocketID());
		}
		
		serversession=null;
	} catch (IOException e) {
		e.printStackTrace();
	}
	 System.out.println("物理关闭socket");
}

/**
 * 读取数据
 * 返回接收的字节大小
 */
public int readData(byte[]data)
{
    if(getCloseState())
     {
	   return -1;
     }
	try {
	  int r=socket.getInputStream().read(data);
	  //readLen+=r;
	 return r;
	} catch (IOException e) {
		e.printStackTrace();
	}
	return -1;
}

/**
 * 读取全部数据
 */
public byte[] readALL()
{
	 byte[] result=null;
	  if(socket!=null)
	  {
		  byte[]  readBytes=new byte[bufSize];//接收区
		  int r=0;
		  while(true)
		  {
			  if(getCloseState())
				{
					return null;
				}
		       r=readData(readBytes);
		      if(r==-1)
		      {
		    	  result=pack.getData();
		    	  break;
		      }
		      else
		      {
		         // readLen+=r;
		    	  if(r==0)
		    	  {
		    		  try {
						TimeUnit.MILLISECONDS.sleep(100);
						
						continue;
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
		    	  }
		    	 //
		    		  byte[] buf=new byte[r];
		    		  System.arraycopy(readBytes, 0, buf, 0, r);
		    		  if(pack.addData(buf))
		    		  {
		    			  result=pack.getData();
		    			  break;
		    		  }
		    	 
		    	  
		      }
		  } 
		
	  }
	  
	  return result;
}


/*
 * 获取初始化序列
 */
public long getInitSeqNo()
{
	if(socket!=null)
	{
	   return	socket.getSession().getInitialSequenceNumber();
	}
	return 0;
}

/**
 * 发送包长
 */
public int getDataStreamLen()
{
    return socket.getSession().getDatagramSize();
}

/**
 * 目的socket
 * @return
 */
public Destination getDestination()
{

    if(socket!=null)
    {
       return   socket.getSession().getDestination();
    }
    Destination tmp = null;
    try {
        tmp = new Destination(InetAddress.getLocalHost(), 0);
    } catch (UnknownHostException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
    }
    return tmp;
}
/**
 * 发送数据
 * 空数据不能发送
 */
public boolean sendData(byte[]data) {
	if(getCloseState())
	{
		return false;
	}
	try {
		
		socket.getOutputStream().write(data);
		socket.getOutputStream().flush();
		sendLen=+1;
		return true;
	} catch (IOException e) {
		e.printStackTrace();
	}
	return false;
}

/**
 * 分包发送数据
 * 会再次分割数据，同时添加头
 * 对应的要用readALL
 * @param data
 * @return
 */
public boolean sendSplitData(byte[]data) {
	if(getCloseState())
	{
		return false;
	}
	 byte[][]result=null;
	 if(dataLen==0)
	 {
		 result= PackagetSub.splitData(data);
	 }
	 else
	 {
		 PackagetSub sub=new PackagetSub();
		 result=sub.split(data, dataLen);
	 }
	 for(int i=0;i<result.length;i++)
	 {
		 if(!sendData(result[i]))
		 {
			 //一次发送失败则返回失败
			 return false;
		 }
	 }
	return true;
}
/**
 * 获取远端host
 * @return
 */
public String getRemoteHost() {
return	socket.getSession().getDestination().getAddress().getHostName();
	
}

/**
 * 获取远端端口
 * @return
 */
public int getRemotePort() {
  return	socket.getSession().getDestination().getPort();
}

/**
 * socketid
 * @return
 */
public long getID() {
	
	return socketID;
}
/**
 * 设置是读取为主还是写入为主
 * 如果是写入为主，当读取速度慢时，数据覆盖丢失
 * 默认读取为主，还没有读取则不允许覆盖，丢掉数据，等待重复
 * 设置大数据读取才有意义
 * @param isRead
 */
public void  setBufferRW(boolean isRead)
{
	try {
		socket.getInputStream().resetBufMaster(isRead);
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
}

/**
 * 设置大数据读取
 * 默认 false
 * @param islarge
 */
public void setLargeRead(boolean islarge)
{
	try {
		socket.getInputStream().setLargeRead(islarge);
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
}


}
