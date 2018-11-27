package de.blankedv.sx4monitor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;

import java.net.Socket;
import java.net.SocketAddress;
import android.content.Context;
import android.os.Message;
import android.util.Log;

import static de.blankedv.sx4monitor.SX4MonitorApplication.*;
/**
 * communicates with the SX3-PC server program (usually on port 4104)
 * 
 * runs on own thread, using a BlockingQueue for queing the commands
 * can be shutdown by calling the shutdown method.
 * 
 * @author mblank
 *
 */
public class SXnetClientThread extends Thread {
	// threading und BlockingQueue siehe http://www.javamex.com/tutorials/blockingqueue_example.shtml

	private volatile boolean shuttingDown, clientTerminated;

	private Context context;   
	private long lastReceived;

	private static final int ERROR = 9999;

	private boolean shutdownFlag;

	private String ip;
	private int port;
	private Socket socket;
	private PrintWriter out = null;
	private BufferedReader in = null; 


	public SXnetClientThread(Context context, String ip, int port) {
		if (DEBUG) Log.d(TAG,"SXnetClientThread constructor.");
		this.context=context;
		this.ip = ip;
		this.port = port;
		shuttingDown=false;
		clientTerminated=false;
		shutdownFlag=false;
		lastReceived = System.currentTimeMillis() + 5000;  // initialize
	}

	public void shutdown() {
		shutdownFlag=true;
	}

	public void run(){
		if (DEBUG) Log.d(TAG,"SXnetClientThread run.");
        shutdownFlag = false;
        clientTerminated = false;
        connect();
   

		while ((shutdownFlag == false) && (!Thread.currentThread().isInterrupted())) {   
			try {
				if ((in != null) && (in.ready())) {
					String in1 = in.readLine();
					if (DEBUG) Log.d(TAG,"msgFromServer: " + in1);
                    String[] cmds = in1.split(";");  // multiple commands per line possible, separated by semicolon
                    for (String cmd : cmds) {
                        handleMsgFromServer(cmd.trim().toUpperCase());
                        // sends feedback message  XL 'addr' 'data' (or INVALID_INT) back to mobile device
                    }
                    lastReceived = System.currentTimeMillis();
				}
			} catch (IOException e) {
				Log.e(TAG,"ERROR: reading from socket - "+e.getMessage());
			}

			// check send queue
			if (!sendQ.isEmpty()) {
	
				String comm="";
				try {
					comm = sendQ.take();
					if (comm.length()>0) immediateSend(comm);
				} catch (InterruptedException e) {
					Log.e(TAG,"could not take command from sendQ");
				}

			}

			// send a command at least every 10 secs
			if ((System.currentTimeMillis() - lastReceived) > 10*1000) {
				Log.e(TAG,"SXnetClientThread - connection lost? ");
				Message m = Message.obtain();
				m.what = ERROR_MESSAGE;
				m.obj = "disconnected from SX4 server";
				handler.sendMessage(m);  // send SX data to UI Thread via Message
				lastReceived = System.currentTimeMillis();  // send this msg only every 10 secs
			}
		}

        clientTerminated = true;
        if (socket != null) {
        	try {
				socket.close();
				Log.e(TAG,"SXnetClientThread - socket closed");
			} catch (IOException e) {
				Log.e(TAG,"SXnetClientThread - "+e.getMessage());
			}
        }
		if (DEBUG) Log.d(TAG,"SXnetClientThread stopped.");			
	}


	private void connect() {
		if (DEBUG) Log.d(TAG,"SXnet trying conn to - "+ip+":"+port);
		try {
			SocketAddress socketAddress = new InetSocketAddress(ip, port);

			// create a socket
			socket = new Socket();
			socket.connect(socketAddress, 2000);
			//socket.setSoTimeout(2000);  // set read timeout to 2000 msec   

			//socket.setSoLinger(true, 0);  // force close, dont wait.

			out = new PrintWriter(socket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
			connString = in.readLine();
			lastReceived = System.currentTimeMillis();

			if (DEBUG) Log.d(TAG,"SXnet connected to: "+connString);

		} catch (Exception e) {
			Log.e(TAG,"SXnetClientThread.connect - Exception: "+e.getMessage());

            Message m = Message.obtain();
            m.what = ERROR_MESSAGE;
            m.obj = e.getMessage();
            handler.sendMessage(m);  // send SX data to UI Thread via Message
		}
 	}

	public void disconnectContext() {
		this.context = null;
		Log.d(TAG,"SXnet lost context, stopping thread");
		shutdown();
	}

	/* public void readChannel(int adr) {

		if (DEBUG) Log.d(TAG,"readChannel a="+adr+" shutd.="+shuttingDown+" clientTerm="+clientTerminated);
		if ( shutdownFlag || clientTerminated || (adr == INVALID_INT)) return;
		String command = "R "+adr;
		Boolean success = sendQ.offer(command);
		if ((success == false) && (DEBUG)) Log.d(TAG,"readChannel failed, queue full")	;
	} */


	private void immediateSend(String command) {
		if (shutdownFlag || clientTerminated ) return;
		if (out == null) {
			if (DEBUG) Log.d(TAG,"out=null, could not send: "+command);
		} else {
			try {	
				out.println(command);
				out.flush();
				if (DEBUG) Log.d(TAG,"sent: "+command);
			}
			catch (Exception e) {
				if (DEBUG) Log.d(TAG,"could not send: "+command);
				Log.e(TAG, e.getClass().getName() + " "+ e.getMessage()); 
			}
		}
	}


	/**
	 * SX Net Protocol (all msg terminated with CR)
	 *
	 * for a list of channels (which the client has set or read in the past) all changes are 
	 *                    transmitted back to the client
	 */
 
	private void handleMsgFromServer(String msg) {
		// check whether there is an application to send info to -
		// to avoid crash if application has stopped but thread is still running
		if (context == null) return; 
		
		String[] info = null;
		msg=msg.toUpperCase();

		int adr;
		int data;

		if( (msg.length() != 0) && 
				(!msg.contains("ERROR")) && 
				(!msg.contains("OK"))
				)  { // message should contain valid data
			info = msg.split("\\s+");  // one or more whitespace

			if ( (info.length >= 2) && info[0].equals("XPOWER") ) {
				data = getDataFromString(info[1]);
				if ((data != ERROR) ) {
					Message m = Message.obtain();
					m.what = POWER_MESSAGE;
					m.arg1 = 0;
					m.arg2 = data;
					handler.sendMessage(m);  // send SX data to UI Thread via Message
				}
			} else if ( (info.length >= 3) && info[0].equals("X") ) {
				adr = getChannelFromString(info[1]);
				data = getDataFromString(info[2]);
				if (( adr != ERROR) && (data != ERROR) ) {
					Message m = Message.obtain();
					m.what = SX_FEEDBACK_MESSAGE;
					m.arg1 = adr;
					m.arg2 = data;
					handler.sendMessage(m);  // send SX data to UI Thread via Message
				}
			}
		}
	}

	private int getDataFromString(String s) {
		// converts String to integer between 0 and 255 (=SX Data)
		Integer data=ERROR;
		try {
			data = Integer.parseInt(s);
			if ( (data < 0 ) || (data >255)) {
				data = ERROR;
			} 
		} catch (Exception e) {
			data = ERROR;
		}
		return data;
	}

	int getChannelFromString(String s) {
		Integer channel=ERROR;
		try {
			channel = Integer.parseInt(s);
			if ( (channel >= 0 ) && (channel <= SXMAX)) {
				return channel;
			} else {
				channel = ERROR;
			}
		} catch (Exception e) {

		}
		return channel;
	}



}
