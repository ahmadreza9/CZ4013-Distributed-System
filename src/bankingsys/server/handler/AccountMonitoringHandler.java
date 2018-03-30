package bankingsys.server.handler;

import bankingsys.message.ServiceRequest;
import bankingsys.message.ServiceResponse;
import bankingsys.server.model.BankAccount;
import bankingsys.server.model.Client;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;

import static bankingsys.message.ServiceResponse.ResponseType.FAILURE;
import static bankingsys.message.ServiceResponse.ResponseType.SUCCESS;

/**
 * Handler for registering monitoring clients
 */
public class AccountMonitoringHandler extends ServiceHandler {
    private HashSet<Client> clients;

    public AccountMonitoringHandler(HashMap<Integer, BankAccount> accounts, HashSet<Client> clients) {
        super(accounts);
        this.clients = clients;
    }

    @Override
    public ServiceResponse handleRequest(ServiceRequest request) {
        System.out.println("AccountMonitoringHandler called");
        if (!clients.contains(new Client(request.getRequestAddress(), request.getRequestPort()))) {
            clients.add(new Client(request.getRequestAddress(), request.getRequestPort()));
            return new ServiceResponse(SUCCESS, null, "Monitoring callback registered", null);
        }
        return new ServiceResponse(FAILURE, null, "Monitoring callback already registered", null);
    }
}
