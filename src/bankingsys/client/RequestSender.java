package bankingsys.client;

import bankingsys.io.Deserializer;
import bankingsys.io.Serializer;
import bankingsys.message.ServiceRequest;
import bankingsys.message.ServiceResponse;
import bankingsys.server.model.BankAccount;

import java.io.IOException;
import java.net.*;
import java.util.Scanner;

import static bankingsys.Constant.BUFFER_SIZE;
import static bankingsys.Constant.SERVER_PORT;

/**
 * Main class that implements the client
 *
 * Available commands:
 * - Create Name Password CurrencyType Amount
 * - Close Name AccountNumber Password
 * - Deposit Name AccountNumber Password CurrencyType Amount
 * - Withdraw Name AccountNumber Password CurrencyType Amount
 * - Moniter
 * - Check Name AccountNumber Password
 * - Transfer Name AccountNumber Password TargetAccount Amount
 */

public class RequestSender {

    public static void main(String args[]) throws IOException {
        DatagramSocket socket = null;
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            socket = new DatagramSocket();
            Scanner sc = new Scanner(System.in);
            while (true) {
                System.out.print(">>> ");
                String command = sc.nextLine();
                String[] commandSplits = command.split(" ");
                String commandType = commandSplits[0];

                ServiceRequest request = null;
                switch (commandType){
                    case "Create":
                        request = new ServiceRequest(
                                'b',
                                commandSplits[1],
                                null,
                                commandSplits[2],
                                Float.parseFloat(commandSplits[4]),
                                null,
                                BankAccount.Currency.valueOf(commandSplits[3]));
                        break;
                    case "Close":
                        request = new ServiceRequest(
                                'a',
                                commandSplits[1],
                                Integer.parseInt(commandSplits[2]),
                                commandSplits[3],
                                null,
                                null,
                                null);
                        break;
                    case "Deposit":
                        request = new ServiceRequest(
                                'e',
                                commandSplits[1],
                                Integer.parseInt(commandSplits[2]),
                                commandSplits[3],
                                -Float.parseFloat(commandSplits[5]),
                                null,
                                BankAccount.Currency.valueOf(commandSplits[4]));
                        break;
                    case "Withdraw":
                        request = new ServiceRequest(
                                'e',
                                commandSplits[1],
                                Integer.parseInt(commandSplits[2]),
                                commandSplits[3],
                                Float.parseFloat(commandSplits[5]),
                                null,
                                BankAccount.Currency.valueOf(commandSplits[4]));
                        break;
                    case "Moniter":
                        break;
                    case "Check":
                        request = new ServiceRequest(
                                'd',
                                commandSplits[1],
                                Integer.parseInt(commandSplits[2]),
                                commandSplits[3],
                                null,
                                null,
                                null);
                        break;
                    case "Transfer":
                        request = new ServiceRequest(
                                'f',
                                commandSplits[1],
                                Integer.parseInt(commandSplits[2]),
                                commandSplits[3],
                                Float.parseFloat(commandSplits[5]),
                                Integer.parseInt(commandSplits[4]),
                                null);
                        break;
                }

                if (request != null) {
                    Serializer serializer = new Serializer();
                    request.write(serializer);

                    InetAddress address = InetAddress.getByName("localhost");
                    DatagramPacket packet = new DatagramPacket(serializer.getBuffer(), serializer.getBufferLength(),
                            address, SERVER_PORT);
                    socket.send(packet);

                    DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
                    socket.receive(reply);
                    Deserializer deserializer = new Deserializer(buffer);
                    ServiceResponse response = new ServiceResponse();
                    response.read(deserializer);
                    System.out.println(response.getResponseCode());
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
}
