package handsOn.circularEconomy.agents;

import handsOn.circularEconomy.data.Part;
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
        Color customColor = new Color(58, 135, 84);
        this.window.setBackgroundTextColor(customColor);
        AgentServicesTools.register(this, "repair", "partstore");
        println("hello, I'm just registered as a parts-store");
        println("do you want some special parts ?");
        Random hasard = new Random();
        parts = new ArrayList<>();
        var existingParts = Part.getListParts();
        while (parts.size() < 10) {
            for (Part p : existingParts) {
                if (hasard.nextBoolean()) {
                    parts.add(new Part(p.getName(), p.getType(), p.getStandardPrice() * (1 + Math.random() * 0.3), p.getBreakdownLevel()));
                }
                if (parts.size() == 10) {
                    break;
                }
            }
        }
        println("here are the parts I sell : ");
        parts.forEach(p->println("\t"+p));

        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                ACLMessage message = receive(MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
                if (message != null) {
                    if(Objects.equals(message.getConversationId(), "Ask-Part-Price")) {
                        Part requestedPartPrice = null;
                        try {
                            requestedPartPrice = (Part) message.getContentObject();
                        } catch (UnreadableException e) {
                            throw new RuntimeException(e);
                        }
                        double partPrice = getPartPrice(requestedPartPrice);
                        sendReplyToUser(message.getSender(), partPrice);
                    }
                    if (Objects.equals(message.getConversationId(), "Buy-Part")) {
                        Part requestedPart = null;
                        try {
                            requestedPart = (Part) message.getContentObject();
                        } catch (UnreadableException e) {
                            throw new RuntimeException(e);
                        }
                        // Supprimer la pièce vendue de la liste
                        if (removeSoldPart(requestedPart)) {
                            println("Part " + requestedPart.getName() + " sold to " + message.getSender().getLocalName());
                        } else {
                            println("Error: Part " + requestedPart.getName() + " not found in the part store");
                        }
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
        replyMessage.setConversationId("Ask-Part-Price");
        try {
            replyMessage.setContentObject(partPrice);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        send(replyMessage);
    }
    // Méthode pour supprimer la pièce vendue de la liste
    private boolean removeSoldPart(Part soldPart) {
        return parts.removeIf(part -> Objects.equals(part.getName(), soldPart.getName()));
    }

}
