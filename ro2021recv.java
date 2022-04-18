/*****************************************************************
Aplicacion receptora (simula al servidor)

Sintaxis:
    java ro2021recv output_file listen_port
    javac ro2021recv1.java
    java ro2021recv1 salida.txt 8000

********************************************************************/

import java.io.*;
import java.net.*;
import java.nio.*;

public class ro2021recv {
    public static void main(String[] args){
        if(args.length != 2){
            System.out.println("La sintaxis correcta es: java ro2021recv output_file listen_port");
            System.exit(0);
        }

        final String output_file = args[0];
        final int listen_port = Integer.parseInt(args[1]);
      
        try{

            /****Apertura del fichero de salida y del socket*****/
            DataOutputStream dataOutputStream = new DataOutputStream(new FileOutputStream(output_file));
            DatagramSocket serverSocket = new DatagramSocket(listen_port);
            /***************************************************/

            int N_esperado = 0;     //Numero de secuencia que se espera recibir (aumenta con cada paquete recibido en correcto orden)

            boolean continua = true;
            while(continua){
                
                /********Recepcion del paquete ******************/
                DatagramPacket paquete_in = new DatagramPacket(new byte[1472],1472);
                serverSocket.receive(paquete_in);           //Recibimos el paquete del cliente
                /************************************************/

                //System.out.println("Socket recibido");
                /*******Direccion SuffleRouter*******************/
                //Leemos la direccion y el puerto del shuffle a partir del paquete recibido
                InetAddress shuffle_Address = paquete_in.getAddress();
                int shuffle_Port = paquete_in.getPort();

                SocketAddress shuffle_address = new InetSocketAddress(shuffle_Address, shuffle_Port);
                /************************************************/


                /************Lectura de los datos recibidos*****/
                byte[] data_byte = paquete_in.getData();
                ByteBuffer bbuf = ByteBuffer.wrap(data_byte);

                //Leemos la direccion, el puerto del cliente y el numero de secuencia a partir del paquete recibido
                bbuf.rewind();
                int N = bbuf.getInt(6);

                //System.out.println("Ha llegado un paquete con N=" +N+ ", mientras que el esperado es N=" +N_esperado);
                if(N == N_esperado){
                    N_esperado++;

                    bbuf.position(0);
                    byte[] dest_IP = new byte[4];
                    bbuf.get(dest_IP, 0, 4);

                    byte[] dest_port = new byte[2];
                    bbuf.get(dest_port, 0, 2);

                    bbuf.position(0);     
                    int longitud_lectura = bbuf.getShort(10);
                    if(longitud_lectura == -1){
                        //System.out.println("Llegue al final");
                        continua = false;
                        continue;
                    }
                    long timeStamp = bbuf.getLong(12);
                    bbuf.position(20);
                    byte[] datos = new byte[longitud_lectura];
                    bbuf.get(datos,0,longitud_lectura);

                    //System.out.println(new String(datos));
                    dataOutputStream.write(datos);                  //Escribimos los datos en el fichero de salida
                    /************************************************/


                    /*******Creacion del ACK************************/
                    ByteBuffer bbuf_ack = ByteBuffer.allocate(18);
                    bbuf_ack.rewind();
                    bbuf_ack.put(dest_IP);
                    bbuf_ack.put(dest_port);
                    bbuf_ack.putInt(N);
                    bbuf_ack.putLong(timeStamp);
                    //bbuf_ack.putShort((short)1);                 //1 significa ACK, 0 significa NACK

                    /*********Conversion a bytes***************/
                    bbuf_ack.rewind();
                    byte[] bbuf_ack_bytes = new byte[bbuf_ack.remaining()];
                    bbuf_ack.get(bbuf_ack_bytes);
                    /******************************************/

                    /**********Envio del ACK***************/
                    DatagramPacket paquete_out = new DatagramPacket(bbuf_ack_bytes, 18, shuffle_address);
                    serverSocket.send(paquete_out);
                    /************************************************/
                } 

                /*Si el ACK se perdio, el emisor reenvia un paquete que ya hemos escrito en el fichero de salida, es decir,
                    N < N_esperado
                  En este caso, recibimos el paquete y reenviamos el ACK, sin escribir en el fichero de salida ni actualizar N_esperado
                */
                else if(N < N_esperado){  
                    bbuf.position(0);
                    byte[] dest_IP = new byte[4];
                    bbuf.get(dest_IP, 0, 4);

                    byte[] dest_port = new byte[2];
                    bbuf.get(dest_port, 0, 2);

                    long timeStamp = bbuf.getLong(12);

                    /*******Creacion del ACK************************/
                    ByteBuffer bbuf_ack = ByteBuffer.allocate(18);
                    bbuf_ack.rewind();
                    bbuf_ack.put(dest_IP);
                    bbuf_ack.put(dest_port);
                    bbuf_ack.putInt(N);
                    bbuf_ack.putLong(timeStamp);
                    //bbuf_ack.putShort((short)1);                 //1 significa ACK, 0 significa NACK

                    /*********Conversion a bytes***************/
                    bbuf_ack.rewind();
                    byte[] bbuf_ack_bytes = new byte[bbuf_ack.remaining()];
                    bbuf_ack.get(bbuf_ack_bytes);
                    /******************************************/

                    /**********Envio del ACK***************/
                    DatagramPacket paquete_out = new DatagramPacket(bbuf_ack_bytes, 18, shuffle_address);
                    serverSocket.send(paquete_out);
                    /************************************************/                  

                }

            }

            serverSocket.close();
            dataOutputStream.close();


        }catch(Exception e){
            e.printStackTrace();
        }





    }
}

