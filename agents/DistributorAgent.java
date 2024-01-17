package handsOn.circularEconomy.agents;

import handsOn.circularEconomy.data.Part;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class DistributorAgent extends AgentWindowed {
    List<ProductType> products;

    @Override
    public void setup() {
        this.window = new SimpleWindow4Agent(getLocalName(), this);
        Color customColor = new Color(58, 94, 135);
        this.window.setBackgroundTextColor(customColor);
        AgentServicesTools.register(this, "repair", "distributor");
        println("Hello, I'm just registered as a distributor");

        // Randomly decide whether to sell new or used products
        boolean sellUsedProducts = new Random().nextDouble() >= 0.5;
        String productType = sellUsedProducts ? "used" : "new";
        double maxDiscountPercentage = -0.3; // Maximum discount for used products
        println("I sell " + productType + " products.");

        // Create a list of parts with prices adjusted based on whether they are new or used
        products = new ArrayList<>();
        var existingParts = Part.getListParts();

        for (ProductType type : ProductType.values()) {
            double randomDiscount = maxDiscountPercentage * Math.random();
            double adjustedPrice = type.getStandardPrice() * (1.0 + randomDiscount);
            // Modify the standardPrice directly in the ProductType
            type.standardPrice = adjustedPrice;
            products.add(type);
        }

        println("I have these products : ");
        products.forEach(p -> {
            println("\t" + p + " - Price: " + String.format("%.2f",p.getStandardPrice()) + " €");
        });

        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                ACLMessage message = receive(MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
                if (message != null) {
                    if(Objects.equals(message.getConversationId(), "Ask-Product-Price")) {
                        ProductType requestedProductPrice = null;
                        try {
                            requestedProductPrice = (ProductType) message.getContentObject();
                        } catch (UnreadableException e) {
                            throw new RuntimeException(e);
                        }
                        double productPrice = getProductPrice(requestedProductPrice);
                        sendReplyToUser(message.getSender(), productPrice);
                    }
                    if (Objects.equals(message.getConversationId(), "Buy-Product")) {
                        ProductType requestedProduct = null;
                        try {
                            requestedProduct = (ProductType) message.getContentObject();
                        } catch (UnreadableException e) {
                            throw new RuntimeException(e);
                        }
                        // Supprimer la pièce vendue de la liste
                        if (removeSoldProduct(requestedProduct)) {
                            println("Product " + requestedProduct + " sold to " + message.getSender().getLocalName());
                        } else {
                            println("Error: Product " + requestedProduct + " not found in the part store");
                        }
                    }
                } else {
                    block();
                }
            }
        });
    }
    public double getProductPrice(ProductType product) {
        for (ProductType distributorProduct : products) {
            if (Objects.equals(distributorProduct, product)) {
                return distributorProduct.getStandardPrice();
            }
        }
        return -1; // Retourne -1 si le produit n'est pas disponible
    }

    public void sendReplyToUser(AID userAgent, double productPrice) {
        ACLMessage replyMessage = new ACLMessage(ACLMessage.INFORM);
        replyMessage.addReceiver(userAgent);
        replyMessage.setConversationId("Ask-Product-Price");
        if (productPrice >= 0) {
            replyMessage.setContent(": price for the requested product : " + String.valueOf(productPrice));
        } else {
            replyMessage.setContent(": does not have the requested product.");
        }
        send(replyMessage);
    }

    private boolean removeSoldProduct(ProductType soldProduct) {
        return products.removeIf(ProductType -> Objects.equals(products, soldProduct));
    }

}
