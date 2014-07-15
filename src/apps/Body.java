/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package apps;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import static java.lang.Thread.sleep;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.io.ServerSocketConnection;
import javax.microedition.midlet.MIDlet;
import jdk.dio.DeviceConfig;
import jdk.dio.DeviceManager;
import jdk.dio.gpio.GPIOPin;
import jdk.dio.i2cbus.I2CDevice;
import jdk.dio.i2cbus.I2CDeviceConfig;


/**
 *
 * @author drevn_000
 */
public class Body extends MIDlet 
{
   private double temp = 0;
   private I2CDevice temperature_sensor = null;
   private GPIOPin led1 = null;
   private GPIOPin moving_sensor = null;
   private final int LED_PIN_NUM = 23;
   private final int MOVING_PIN_NUM = 17;
   CheckLED checker = null;
   CheckMotion detector = null;
   CheckTemperature measuring = null;
   String hash = null;
   String dev_id = null;
   private final int TEMP_ADRESS = 0x4f;
   private final int FREQ = 100000; 
   private final int ADDRESS_SIZE = 7; 
   private final int BUS_ID = 1; 
   
    
   private final int REG_READ_TEMP = 0xAA;
   private final int RT_ADDR_SIZE = 0x01;
   private final int READ_TEMP_SIZE = 0x02;
   private final int READ_TEMP_VAL = 0x00;
   private final int REG_ACC_CONF = 0xAC;
   private final int ACC_CONF_VAL = 0x00;
   private final int REG_START_CONV = 0xEE;
   private final int REG_STOP_CONV = 0x22; 
   
   void TurnLEDon(GPIOPin led, int time, boolean blink)
   {
       try
       {
           if (blink)
           {
               int times = 0;
               for (times = 0; times < 10; times++)
               {
                   sleep(time * 50);
                   led.setValue(true);
                   sleep(time * 50);
                   led.setValue(false);
               }
           }
           else
           {
                led.setValue(true);
                sleep(time * 1000);
                led.setValue(false);
           }
       }
       catch (Throwable ex) 
       {
           ex.printStackTrace();
       }
  
       try
       {
           led.setValue(false);
       }
       catch (Throwable ex) 
       {
           ex.printStackTrace();
       }
   }
   
   String getDataFromServer(String serverUrl)
   {
    HttpConnection httpConn = null;
    InputStream is = null;
    String dataRead = "";
    try
    {
        httpConn = (HttpConnection)Connector.open(serverUrl);
        if((httpConn.getResponseCode() == HttpConnection.HTTP_OK))
        {
            int length = (int)httpConn.getLength();
            is = httpConn.openInputStream();
            if(length == -1)
            {//unknown length returned by server.
//It is more efficient to read the data in chunks, so we
//will be reading in chunk of 1500 = Maximum MTU possible
 
                int chunkSize = 1500;
                byte[] data = new byte[chunkSize];
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int dataSizeRead = 0;//size of data read from input stream.
                while((dataSizeRead = is.read(data))!= -1)
                {
//it is not recommended to write to string in the
//loop as it causes heap defragmentation and it is
//inefficient, therefore we use the
//ByteArrayOutputStream.
                    baos.write(data, 0, dataSizeRead );
                    System.out.println("Data Size Read = "+dataSizeRead);
                }
                dataRead = new String(baos.toByteArray());
                baos.close();
            } 
            else
            {//known length
                DataInputStream dis = new DataInputStream(is);
                byte[] data = new byte[length];
//try to read all the bytes returned from the server.
                dis.readFully(data);
                dataRead = new String(data);
            }
            //System.out.println("Data Read from server--\n"+dataRead);
        } 
        else
        {
            System.out.println("\nServer returned unhandled " +
                    "response code. "+httpConn.getResponseCode());
        }
    } 
    catch(Throwable t)
    {
        System.out.println("Exception occurred during GET "+t.toString());
    }
//Since only limited number of network objects can be in open state
//it is necessary to clean them up as soon as we are done with them.
    finally
    {//Networking done. Clean up the network objects
        try
        {
            if(is != null)
                is.close();
        } 
        catch(Throwable t)
        {
            System.out.println("Exception occurred while closing input " +
                    "stream.");
        }
        try
        {
            if(httpConn != null)
                httpConn.close();
        } 
        catch(Throwable t)
        {
            System.out.println("Exception occurred "+t.toString());
        }
    }
    return dataRead;
   }
    
    public void getLocalIP()
    {
        String msg = null;
        //String msg2 = null;
        try 
        {
            ServerSocketConnection ssc = null;
            try 
            {                                           
               ssc = (ServerSocketConnection) Connector.open("socket://:1234");
            } 
            catch (IOException ex) 
            {
                System.out.println("\nNo IP address received!\n");
                ex.printStackTrace();
            }
                msg = ssc.getLocalAddress();
                System.out.println("Success! Your IP is: " + msg);// + "\nHostname is:\n" + msg2);
                //msg2 = System.getProperty("microedition.hostname");
 
        } 
        catch (IOException ex) 
        {
                ex.printStackTrace();
        }
    }
    
    public class CheckLED extends Thread 
    {
        boolean quit = false;
        public void run()
        {
            quit = false;
            String state = getDataFromServer("http://kdeesh.7ci.ru/led_checking.php?dev_num="+ dev_id +
                    "&dev_id=" + hash);
            if (state.contains("You aren't registered!"))
            {
                System.out.println("LED isn't registered!");
                return;
            }
            try 
            {
                led1 = (GPIOPin) DeviceManager.open(LED_PIN_NUM); //if LED == 1
            }
            catch (IOException ex) 
            {
                System.out.println("open GPIO error, pin = " + LED_PIN_NUM);
                return;
            }
            while( !quit )
            {
                try 
                {
                    sleep(5 * 1000);
                } 
                catch (InterruptedException ex) 
                {
                    System.out.println("Sleeping error");
                    quit = true;
                    return;
                }
                state = getDataFromServer("http://kdeesh.7ci.ru/led_checking.php?dev_num="+ dev_id +
                    "&dev_id=" + hash);
                if (state.contains("1"))
                {
                    try 
                    {
                        led1.setValue(true);
                        //System.out.println(LED_PIN_NUM + " pin is " + led1.getValue() );
                    } 
                    catch (Throwable ex) 
                    {
                        ex.printStackTrace();
                    }
                }
                else if (state.contains("0"))
                {
                    try 
                    {
                        led1.setValue(false);
                        //System.out.println(LED_PIN_NUM + " pin is " + led1.getValue() );
                    } 
                    catch (Throwable ex) 
                    {
                        ex.printStackTrace();
                    }
                }
            }
        }
        public void quit() throws IOException
        {
            quit = true;
            led1.setValue(false);
        }
    }
    
    public class CheckTemperature extends Thread 
    {
        boolean quit = false;
        public void run()
        {
            quit = false;
            while( !quit )
            {
                I2CDeviceConfig config = new I2CDeviceConfig(DeviceConfig.DEFAULT, TEMP_ADRESS, DeviceConfig.DEFAULT, DeviceConfig.DEFAULT);
                try
                {
                    temperature_sensor = (I2CDevice)DeviceManager.open(config);
                    ByteBuffer setup = ByteBuffer.wrap(new byte[]{(byte) REG_ACC_CONF, (byte) ACC_CONF_VAL});
                    temperature_sensor.write(setup);
                    //THERMOSTAT CONFIGURATION START
                    setup.clear();
                    setup = ByteBuffer.wrap(new byte[]{(byte) 0xA1, (byte) 0x1A, (byte) 0x00}); //(0x1A=26 degrees)2 bytes high temperature (after that "1")
                    temperature_sensor.write(setup);
                    setup.clear();
                    setup = ByteBuffer.wrap(new byte[]{(byte) 0xA2, (byte) 0x19, (byte) 0x00}); //(0x19=25 degtees)2 bytes low temperature (after that "0")
                    temperature_sensor.write(setup);
                    //THERMOSTAT STOP
                    setup.clear();
                    setup = ByteBuffer.wrap(new byte[]{(byte) REG_START_CONV});
                    temperature_sensor.write(setup);

                    ByteBuffer readBuf = ByteBuffer.allocateDirect(2);
                    int read = temperature_sensor.read(0xAA, 0x01, readBuf);
                    readBuf.clear();
                    sleep(1000);
                    //НУЖНО ЧЕКНУТЬ ТОТ ЛИ dev_id!
                    while(true)
                    {
                        read = temperature_sensor.read(0xAA, 0x01, readBuf);
                        temp = readBuf.get(0);
                        if (readBuf.get(1) < 0)
                        {
                            temp += 0.5;
                        }
        //                System.out.println("http://kdeesh.7ci.ru/temperature.php?dev_num="+ dev_id +
        //                                "&dev_id=" + hash +
        //                                "&temperature=" + temp);
                        System.out.println(
                                getDataFromServer("http://kdeesh.7ci.ru/temperature.php?dev_num="+ dev_id +
                                        "&dev_id=" + hash +
                                        "&temperature=" + temp)
                        );
                        readBuf.clear();
                        sleep(60000); //60sec
                    }

                }
                catch(IOException ioex)
                {
                    System.out.println( "Reading error!");
                    System.out.println( "Caughted: " + ioex.getLocalizedMessage());
                }
                catch(Throwable ex)
                {
                    ex.printStackTrace();
                }
            }
        }
        public void quit()
        {
            quit = true;
        }
    }
    
    
    
    public class CheckMotion extends Thread 
    {
        boolean quit = false;
        public void run()
        {
            quit = false;
            boolean alarm = true;
            try
            {
                moving_sensor = (GPIOPin) DeviceManager.open(MOVING_PIN_NUM);
            }
            catch (Throwable ex)
            {
                System.out.println("Error while opening " + MOVING_PIN_NUM + "PIN");
                ex.printStackTrace();
                return;
            }
            try 
            {
                led1 = (GPIOPin) DeviceManager.open(LED_PIN_NUM); //if LED == 1
            }
            catch (IOException ex) 
            {
                System.out.println("open GPIO error, pin = " + LED_PIN_NUM);
                ex.printStackTrace();
            }
            while( !quit )
            {
                

                try 
                {
                    alarm = moving_sensor.getValue();
                    System.out.println(MOVING_PIN_NUM + " pin is " + alarm );
                } 
                catch (Throwable ex) 
                {
                    ex.printStackTrace();
                }
                if (!alarm)
                {
                    alarm = true;
                    TurnLEDon(led1, 4, true);
                }
            }
        }
        public void quit() throws IOException
        {
            quit = true;
            moving_sensor.close();
            led1.close();
        }
    }
    
    
    @Override
    public void startApp() 
    {
        detector = new CheckMotion();
        detector.start();
//        //getLocalIP();
//        System.out.println( "Your IP is " + getDataFromServer("http://kdeesh.7ci.ru/get_ip.php") );
//        String system_information = getDataFromServer("http://kdeesh.7ci.ru/connection.php");
//        if (system_information.contains("You aren't registered!"))
//        {
//            System.out.println("Device isn't registered!");
//            return;
//        }
//        else if (system_information.contains("You have more then one device"))
//        {
//            System.out.println("You have more then one device!");
//            return;
//        }
//        else
//        {
//            hash = system_information.substring(1, 33);
//            dev_id = system_information.substring(34);
//            System.out.println("hash id " + hash + " id is " + dev_id);
//        }
//        checker = new CheckLED();
//        checker.start();
//        measuring = new CheckTemperature();
//        measuring.start();
    }
    
    @Override
    public void destroyApp(boolean unconditional) 
    {
       try 
       {
           checker.quit();
           detector.quit();
       } 
       catch (IOException ex) 
       {
           System.out.println("error while closing led or motion sensor");
       }
        measuring.quit();
        
    }
}
