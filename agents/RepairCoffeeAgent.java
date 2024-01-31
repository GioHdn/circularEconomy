package handsOn.circularEconomy.agents;

import handsOn.circularEconomy.data.Part;
import handsOn.circularEconomy.data.Product;
import handsOn.circularEconomy.data.ProductType;
import jade.core.AID;
import jade.core.AgentServicesTools;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.gui.AgentWindowed;
import jade.gui.SimpleWindow4Agent;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** class that represents a repair agent.
 * It is declared in the service repair-coffee.
 * It owns some specialities
 * @author emmanueladam@
 * */
public class RepairCoffeeAgent extends AgentWindowed {
    List<ProductType> specialities;
    int distanceFromUser;
    @Override
    public void setup(){
        this.window = new SimpleWindow4Agent(getLocalName(),this);
        Color customColor = new Color(255, 159, 60);
        this.window.setBackgroundTextColor(customColor);
        println("hello, do you want coffee ?");
        var hasard = new Random();
        var distanceFromUser = hasard.nextInt(10000-5000)+5000;
        specialities = new ArrayList<>();
        for(ProductType type : ProductType.values())
            if(hasard.nextBoolean()) specialities.add(type);
        //we need at least one speciality
        if(specialities.isEmpty()) specialities.add(ProductType.values()[hasard.nextInt(ProductType.values().length)]);
        println("I have these specialities : ");
        println("I am " + distanceFromUser + " meters away from User House");
        specialities.forEach(p->println("\t"+p));
        //registration to the yellow pages (Directory Facilitator Agent)
        AgentServicesTools.register(this, "repair", "coffee");
        println("I'm just registered as a repair-coffee");

        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                ACLMessage message = receive(MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
                if (message != null) {
                    if(Objects.equals(message.getConversationId(), "Appointment")) {
                        ProductType requestedProductType = ProductType.valueOf(message.getContent());
                        boolean canRepair = isAbleToRepair(requestedProductType);
                        sendReplyToUser(message.getSender(), canRepair, distanceFromUser);
                    }else if(Objects.equals(message.getConversationId(), "Faulty-Part")) {
                        Product productToRepair;
                        try {
                            productToRepair = (Product) message.getContentObject();
                        } catch (UnreadableException e) {
                            throw new RuntimeException(e);
                        }
                        Part faultyPart = analyseProduct(productToRepair);
                        Integer breakdownLevel = productToRepair.getDefault().getBreakdownLevel();
                        ACLMessage reply = message.createReply();
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setConversationId("Faulty-Part");
                        try {
                            reply.setContentObject(faultyPart);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        send(reply);
                    }
                } else {
                    block();
                }
            }
        });
    }

    private Part analyseProduct(Product p) {
        return p.getDefault();
    }
    // Méthode publique pour obtenir les spécialités du repair-coffee
    public Boolean isAbleToRepair(ProductType productType) {
        return specialities.contains(productType);
    }
    // Méthode pour envoyer la réponse à l'utilisateur
    public void sendReplyToUser(AID userAgent, boolean canRepair, int distanceFromUser) {
        ACLMessage replyMessage = new ACLMessage(ACLMessage.INFORM);
        replyMessage.addReceiver(userAgent);
        if (canRepair) {
            LocalDate proposedDate = LocalDate.now().plusDays(new Random().nextInt(1)+1);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            String formattedDate = proposedDate.format(formatter);
            replyMessage.setContent("Repair coffee can handle the repair. Proposed date: " + formattedDate + ": distance from user :" + distanceFromUser);
        } else {
            replyMessage.setContent("Repair coffee cannot handle the repair.");
        }
        send(replyMessage);
    }
}
