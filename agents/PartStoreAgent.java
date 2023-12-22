package handsOn.circularEconomy.agents;

import handsOn.circularEconomy.data.Part;
import handsOn.circularEconomy.data.Product;
import handsOn.circularEconomy.data.ProductType;
import jade.core.AID;
import jade.core.AgentServicesTools;
import jade.core.behaviours.CyclicBehaviour;
import jade.gui.AgentWindowed;
import jade.gui.SimpleWindow4Agent;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

import java.awt.*;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;


/** class that represents a part-Store agent.
 * It is declared in the service repair-partstore.
 * It an infitine number of specific part wih a pecific cost ( up to 30% more than the standard price)
 * @author emmanueladam
 * */
public class PartStoreAgent extends AgentWindowed {
    List<Part> parts;

    @Override
    public void setup(){
        this.window = new SimpleWindow4Agent(getLocalName(),this);
        this.window.setBackgroundTextColor(Color.LIGHT_GRAY);
        AgentServicesTools.register(this, "repair", "partstore");
        println("hello, I'm just registered as a parts-store");
        println("do you want some special parts ?");
        Random hasard = new Random();
        parts = new ArrayList<>();
        var existingParts = Part.getListParts();
        for(Part p : existingParts)
            if(hasard.nextBoolean())
                parts.add(new Part(p.getName(), p.getType(), p.getStandardPrice()*(1+Math.random()*.3), p.getbreakdownLevel()));
        //we need at least one speciality
        if(parts.isEmpty()) parts.add(existingParts.get(hasard.nextInt(existingParts.size())));
        println("here are the parts I sell : ");
        parts.forEach(p->println("\t"+p));

        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                ACLMessage message = receive(MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
                if (message != null) {
                    if(Objects.equals(message.getConversationId(), "Ask-Price")) {
                        Part requestedPartPrice = null;
                        try {
                            requestedPartPrice = (Part) message.getContentObject();
                        } catch (UnreadableException e) {
                            throw new RuntimeException(e);
                        }
                        double partPrice = getPartPrice(requestedPartPrice);
                        sendReplyToUser(message.getSender(), partPrice);
                    }
                } else {
                    block();
                }
            }
        });
    }
    // Méthode publique pour obtenir les spécialités du repair-coffee
    public double getPartPrice(Part part) {
        for (Part storePart : parts) {
            if (Objects.equals(storePart.getName(), part.getName())) {
                return storePart.getStandardPrice();
            }
        }
        return -1; // Retourne -1 si la pièce n'est pas disponible
    }

    public void sendReplyToUser(AID userAgent, double partPrice) {
        ACLMessage replyMessage = new ACLMessage(ACLMessage.INFORM);
        replyMessage.addReceiver(userAgent);
        replyMessage.setConversationId("Ask-Price");
        if (partPrice >= 0) {
            replyMessage.setContent(": price for the requested part: " + String.valueOf(partPrice));
        } else {
            replyMessage.setContent(": does not have the requested part.");
        }
        send(replyMessage);
    }

}
