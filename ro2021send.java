/*****************************************************************
Aplicacion emisora (simula al cliente)

Sintaxis:
    java ro2021send input_file dest_IP dest_port emulator_IP emulator_port
	javac ro2021send2.java
	java ro2021send1 entrada.txt 127.0.0.1 8000 127.0.0.1 2021

********************************************************************/
import java.io.*;
import java.net.*;
import java.nio.*;
//import java.util.concurrent.TimeoutException;
import java.util.*;

public class ro2021send {
	
    public static void main(String[] args){
        if(args.length != 5){
            System.out.println("La sintaxis correcta es: java ro2021send input_file dest_IP dest_port emulator_IP emulator_port");
            System.exit(0);
        }

		final String input_file = args[0];
		final String dest_IP = args[1];
        final int dest_port = Integer.parseInt(args[2]);
        final int emulator_port = Integer.parseInt(args[4]);
	
        try {

			/*******Direccion Shufflerouter****************/
            InetAddress emulator_IP = InetAddress.getByName(args[3]);
			SocketAddress shuffle_address = new InetSocketAddress(emulator_IP, emulator_port);
			/**********************************************/


			/*********Lectura del fichero*********************/
			DataInputStream dataInputStream = new DataInputStream(new FileInputStream(input_file));
			//byte[] lectura = new byte[1450];
			int offset = 0;
			/*************************************************/

			/************IP destino en bytes****************/
			byte[] dest_IP_bytes = new byte[4];		//Array de 4 bytes para la IP
			int[] ip = new int[4];
			String[] parts = dest_IP.split("\\.");
			for(int i=0; i<4; i++){
				ip[i] = Integer.parseInt(parts[i]);
				dest_IP_bytes[i] = (byte)ip[i];
			}
			/************************************************/


			/*************Puerto destino en bytes************/
			byte[] dest_port_bytes = new byte[2];     //Array de 2 bytes para el puerto
			dest_port_bytes[1] = (byte)(dest_port);
			dest_port_bytes[0] = (byte)(dest_port >> 8);
			/*************************************************/


			/*****************Numero de secuencia*************/
			int N = 0;			//Un entero ocupa 4 bytes
			/*************************************************/


			/********Variables RTO***************************/
			long rtt, rtt_media, sigma_media, rto;
			double rtt_media_double, sigma_media_double;
			double alfa = 0.125, beta = 0.25;
			/************************************************/

			/********Calculo del primer RTO ****************/
			rtt_media = 10;				//en milisegundos
			sigma_media = 5;
			rto = rtt_media + 4*sigma_media;
			/************************************************/

			DatagramSocket sendSocket = new DatagramSocket();
			
			boolean continua = true;
			while(continua){
				//System.out.println("Estoy dentro");
				
				/****Comprobar que no hemos llegado al final***/
				byte[] lectura = new byte[1452];
				int longitud_lectura = dataInputStream.read(lectura, offset, 1452);	//Devuelve el numero de bytes leidos, -1 si no se leyo nada
				//System.out.println(longitud_lectura);

				if (longitud_lectura == -1){
					continua = false;
					continue;			//Detiene la iteracion actual del while, revisa si se puede volver a cumplir (no es el caso)
				}
				/********************************************/

				boolean continua_timeout = true;			//Vale "true" cuando vence el timeout y hay que reenviar el paquete
				while(continua_timeout){
					try{
						continua_timeout = false;			//Si no vence el timeout (catch) se sale del bucle en la siguiente iteracion

						/**********Creacion del paquete*************/
						/*El tamaño del paquete (datos+cabecera) es de 1472 bytes*/
						ByteBuffer bbuf = ByteBuffer.allocate(1472);
						bbuf.rewind();
						bbuf.put(dest_IP_bytes);
						bbuf.put(dest_port_bytes);
						bbuf.putInt(N);
						bbuf.putShort((short)longitud_lectura);
						//long timeStamp = System.nanoTime();
						long timeStamp = System.currentTimeMillis();
						bbuf.putLong(timeStamp);
						//System.out.println(new String(lectura));
						bbuf.put(lectura);
						/******************************************/


						/*********Conversion a bytes***************/
						bbuf.rewind();
						byte[] bbuf_bytes = new byte[bbuf.remaining()];
						bbuf.get(bbuf_bytes);
						/******************************************/

						/**********Envio del paquete***************/
						DatagramPacket paquete_out = new DatagramPacket(bbuf_bytes, 1472, shuffle_address);
						sendSocket.send(paquete_out);
						/******************************************/

						long rto_restante = rto;


						boolean ACK_incorrecto = true;
						while(ACK_incorrecto){

							long t1 = System.currentTimeMillis();
							sendSocket.setSoTimeout((int)rto_restante);
							long t2 = System.currentTimeMillis();

							rto_restante = rto_restante - (t2-t1);


							/************Recepcion del ACK*************/
							DatagramPacket paquete_in = new DatagramPacket(new byte[18],18);
							sendSocket.receive(paquete_in);           //Recibimos el ACK del cliente
							//System.out.println("Se ha recibido el ACK");


							/************Lectura de los datos recibidos*****/
							byte[] data_byte = paquete_in.getData();
							ByteBuffer bbuf_ack = ByteBuffer.wrap(data_byte);

							//Comprobamos si el ACK recibido es el esperado
							bbuf_ack.rewind();
							int N_ack = bbuf_ack.getInt(6);
							//System.out.println("El N recibido es " +N_ack+ ", y el esperado es " +N);

							//if(N_ack == N){
							bbuf_ack.position(0);
							long timeStamp_ack = bbuf_ack.getLong(10);

							rtt = System.currentTimeMillis() - timeStamp_ack;

							/************Calculo de RTO*****************/
							if(N == 0){
								rtt_media = rtt;
								sigma_media = rtt/2;
							}
							else{
								rtt_media_double = (1-alfa)*rtt_media + alfa*rtt;
								sigma_media_double = (1-beta)*sigma_media + beta*(Math.abs(rtt_media_double - rtt));

								long rtt_media_long = (long)rtt_media_double;
								if(rtt_media_double-0.5 > rtt_media_long){
									rtt_media = rtt_media_long + 1;
								}
								else{
									rtt_media = rtt_media_long;
								}

								long sigma_media_long = (long)sigma_media_double;
								if(sigma_media_double-0.5 > sigma_media_long){
									sigma_media = sigma_media_long + 1;
								}
								else{
									sigma_media = sigma_media_long;
								}
							}

							rto = rtt_media + 4*sigma_media;
							/*******************************************/

							if(N_ack == N){
								ACK_incorrecto = false;
								N++;
							}

						//}
						//else{
							//continua_timeout = true;		//Si el N del ACK no es el esperado, se reenvia el paquete
							//System.out.println("Ha llegado un ACK con N=" +N_ack+ " y el esperado tiene N=" +N+ ".Se reenvia el paquete con N=" +N);
						//}
						
						}
						
					}catch(SocketTimeoutException e){
						//System.out.println("El ACK no ha llegado. Se reenvia el paquete con N=" +N);
						continua_timeout = true;
					}

				}

				
				//sendSocket.close();
	    	}

			
			/******Paquete final (sin datos, longitud -1)***/
			ByteBuffer bbuf = ByteBuffer.allocate(1472);
			bbuf.rewind();
			bbuf.put(dest_IP_bytes);
			bbuf.put(dest_port_bytes);
			bbuf.putInt(N);
			bbuf.putInt(-1);
			long t_send = System.currentTimeMillis();
			bbuf.putLong(t_send);
			//bbuf.put(lectura);

			bbuf.rewind();
            byte[] bbuf_bytes = new byte[bbuf.remaining()];
            bbuf.get(bbuf_bytes);

			DatagramPacket paquete_out = new DatagramPacket(bbuf_bytes, 1472, shuffle_address);
			sendSocket.send(paquete_out);
			/***********************************************/

			sendSocket.close();

	    
			dataInputStream.close();

        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
