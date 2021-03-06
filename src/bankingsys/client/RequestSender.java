package bankingsys.client;

import bankingsys.io.Deserializer;
import bankingsys.io.Serializer;
import bankingsys.message.ServiceRequest;
import bankingsys.message.ServiceResponse;
import bankingsys.net.SocketHelper;
import bankingsys.net.UnreliableDatagramSocket;
import bankingsys.server.model.BankAccount;

import java.io.IOException;
import java.net.*;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.*;

import static bankingsys.Constant.*;
import static bankingsys.message.ServiceResponse.ResponseStatus.SUCCESS;

/**
 * Main class that implements the client
 *
 * Available commands:
 * - create Name Password CurrencyType Amount
 * - close Name AccountNumber Password
 * - deposit Name AccountNumber Password CurrencyType Amount
 * - withdraw Name AccountNumber Password CurrencyType Amount
 * - monitor Interval
 * - check Name AccountNumber Password
 * - transfer Name AccountNumber Password TargetAccount Amount
 */
public class RequestSender {

    private static final Logger logger = Logger.getLogger(RequestSender.class.getName());
    private static Options options = new Options();

    private static Boolean simulation = false;

    private int clientPort;
    private static InetAddress serverAddress;
    private DatagramSocket socket = null;
    private byte[] buffer = new byte[BUFFER_SIZE];
    private Integer requestID = 0;

    /**
     * Parse input arguments and start the client
     * @param args CLI arguments
     */
    public static void main(String args[]) {
        options.addOption("h", "help", false, "Show help.");
        options.addOption("sim", "simulation", false, "Set mode to 'simulation' with error rate.");
        options.addOption("s", "server", true, "Set server to connect to.");
        CommandLineParser parser = new DefaultParser();

        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);

            if (cmd.hasOption("h"))
                help();

            if (cmd.hasOption("s")) {
                logger.log(Level.INFO, "Connecting to server " + cmd.getOptionValue("s"));
                serverAddress = InetAddress.getByName(cmd.getOptionValue("s"));
            } else {
                logger.log(Level.INFO, "No server address specified, using localhost");
                serverAddress = InetAddress.getByName("localhost");
            }

            if (cmd.hasOption("sim")) {
                logger.log(Level.INFO, "Using cli argument -sim");
                // Whatever you want to do with the setting goes here
                simulation = true;
            }

        } catch (ParseException e) {
            logger.log(Level.SEVERE, "Failed to parse command line properties", e);
            help();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to parse given server address");
            return;
        }

        new RequestSender().run();
    }

    /**
     * Main method for the client, accepts user input and sends it to the server
     * to get responses
     */
    private void run() {
        try {
            if (simulation) {
                socket = new UnreliableDatagramSocket();
            } else {
                socket = new DatagramSocket();
            }
            clientPort = socket.getLocalPort();
            socket.setSoTimeout(TIMEOUT);
            Scanner sc = new Scanner(System.in);
            while (true) {
                try {
                    System.out.print(">>> ");
                    String command = sc.nextLine();
                    String[] commandSplits = command.split(" ");
                    String commandType = commandSplits[0];

                    ServiceRequest request = null;
                    switch (commandType) {
                        case "create":
                            if (commandSplits[2].length() != PASSWORD_LENGTH) {
                                logger.log(Level.SEVERE, "Password length must be " + PASSWORD_LENGTH);
                                continue;
                            }
                            request = new ServiceRequest(
                                    requestID,
                                    ACCOUNT_CANCEL,
                                    commandSplits[1],
                                    null,
                                    commandSplits[2],
                                    Float.parseFloat(commandSplits[4]),
                                    null,
                                    BankAccount.Currency.valueOf(commandSplits[3]),
                                    null);
                            break;
                        case "close":
                            request = new ServiceRequest(
                                    requestID,
                                    ACCOUNT_CREATE,
                                    commandSplits[1],
                                    Integer.parseInt(commandSplits[2]),
                                    commandSplits[3],
                                    null,
                                    null,
                                    null,
                                    null);
                            break;
                        case "deposit":
                            request = new ServiceRequest(
                                    requestID,
                                    BALANCE_UPDATE,
                                    commandSplits[1],
                                    Integer.parseInt(commandSplits[2]),
                                    commandSplits[3],
                                    Float.parseFloat(commandSplits[5]),
                                    null,
                                    BankAccount.Currency.valueOf(commandSplits[4]),
                                    null);
                            break;
                        case "withdraw":
                            request = new ServiceRequest(
                                    requestID,
                                    BALANCE_UPDATE,
                                    commandSplits[1],
                                    Integer.parseInt(commandSplits[2]),
                                    commandSplits[3],
                                    -Float.parseFloat(commandSplits[5]),
                                    null,
                                    BankAccount.Currency.valueOf(commandSplits[4]),
                                    null);
                            break;
                        case "monitor":
                            request = new ServiceRequest(
                                    requestID,
                                    ACCOUNT_MONITOR,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    Integer.parseInt(commandSplits[1]));
                            break;
                        case "check":
                            request = new ServiceRequest(
                                    requestID,
                                    BALANCE_CHECK,
                                    commandSplits[1],
                                    Integer.parseInt(commandSplits[2]),
                                    commandSplits[3],
                                    null,
                                    null,
                                    null,
                                    null);
                            break;
                        case "transfer":
                            request = new ServiceRequest(
                                    requestID,
                                    TRANSFER,
                                    commandSplits[1],
                                    Integer.parseInt(commandSplits[2]),
                                    commandSplits[3],
                                    Float.parseFloat(commandSplits[5]),
                                    Integer.parseInt(commandSplits[4]),
                                    null,
                                    null);
                            break;
                        case "exit":
                            return;

                    }

                    if (request != null) {
                        Serializer serializer = new Serializer();
                        request.write(serializer);

                        DatagramPacket packet = new DatagramPacket(serializer.getBuffer(), serializer.getBufferLength(),
                                serverAddress, SERVER_PORT);

                        ServiceResponse response = sendRequest(packet);
                        if (request.getRequestType() == ACCOUNT_MONITOR && response.getResponseCode() == SUCCESS) {
                            startMonitoring(Integer.parseInt(commandSplits[1]));
                        }
                        requestID++;
                    } else {
                        logger.log(Level.SEVERE, "Command parse error.");
                    }
                } catch (Exception e ){
                    e.printStackTrace();
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    /**
     * Helper function to send a request and obtain a response
     * @param packet Request to send
     * @return Response received
     */
    private ServiceResponse sendRequest(DatagramPacket packet) {
        DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
        try {
            SocketHelper.sendReliably(socket, packet, reply);
            Deserializer deserializer = new Deserializer(buffer);
            ServiceResponse response = new ServiceResponse();
            response.read(deserializer);
            System.out.println(response.getResponseCode() + "\n" + response.getResponseMessage());
            return response;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Function for handling the monitoring session, keeps listening for
     * server callbacks
     * @param duration The monitoring interval
     */
    private void startMonitoring(int duration) {
        try {
            // create a timer to terminate monitoring after the interval
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        DatagramSocket socket = new DatagramSocket();
                        ServiceResponse response = new ServiceResponse(END_MONITOR, SUCCESS,
                                null, "Monitoring finished", null);
                        Serializer serializer = new Serializer();
                        response.write(serializer);
                        InetAddress address = InetAddress.getByName("localhost");
                        DatagramPacket packet = new DatagramPacket(serializer.getBuffer(),
                                serializer.getBufferLength(), address, clientPort);
                        socket.send(packet);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }, duration * 1000);
            // start monitoring
            socket.setSoTimeout(0);
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                Deserializer deserializer = new Deserializer(buffer);
                ServiceResponse response = new ServiceResponse();
                response.read(deserializer);
                logger.log(Level.INFO, "Type: " + response.getResponseType());
                logger.log(Level.INFO, "Type: " + response.getResponseMessage());
                if (response.getResponseType() == END_MONITOR) {
                    socket.setSoTimeout(TIMEOUT);
                    return;
                }
                System.out.println("Update: Account No. " + response.getResponseAccount() +
                        " now has balance " + response.getResponseAmount());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Prints help message for providing input arguments
     */
    private static void help() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Main", options);
        System.exit(0);
    }
}
