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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class DistributorAgent extends AgentWindowed {
    List<Product> products;

    @Override
    public void setup() {
        this.window = new SimpleWindow4Agent(getLocalName(), this);
        Color customColor = new Color(58, 94, 135);
        this.window.setBackgroundTextColor(customColor);
        // Randomly decide whether to sell new or used products
        boolean sellUsedProducts = new Random().nextDouble() >= 0.5;
        String productCondition = sellUsedProducts ? "used" : "new";
        double maxDiscountPercentage = 0;
        double minDiscountPercentage = 1;
        if (productCondition == "used"){
            AgentServicesTools.register(this, "repair", "used-distributor");
            println("Hello, I'm just registered as a second hand distributor");
            maxDiscountPercentage = -0.3;
            minDiscountPercentage = 0.8;
        }else{
            AgentServicesTools.register(this, "repair", "new-distributor");
            println("Hello, I'm just registered as a new distributor");
        }
        println("I sell " + productCondition + " products.");

        // Create a list of parts with prices adjusted based on whether they are new or used
        products = new ArrayList<>();
        var existingParts = Part.getListParts();

        for (ProductType type : ProductType.values()) {
            double randomDiscount = maxDiscountPercentage * Math.random();
            double adjustedPrice = type.getStandardPrice() * (minDiscountPercentage + randomDiscount);

            // Créer une nouvelle instance de Product avec le prix ajusté
            Product adjustedProduct = new Product(type + "-" + products.size(), type);
            adjustedProduct.price = adjustedPrice;

            // Ajouter la nouvelle instance à la liste
            products.add(adjustedProduct);
        }

        println("I have these products : ");
        products.forEach(p -> {
            println("\t" + p.getType() + " - Price: " + String.format("%.2f", p.getPrice()) + " €");
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
                            println("Error: Product " + requestedProduct + " not found in the distributor");
                        }
                    }
                } else {
                    block();
                }
            }
        });
    }
    public double getProductPrice(ProductType productType) {
        for (Product product : products) {
            if (Objects.equals(product.getType(), productType)) {
                return product.getPrice();
            }
        }
        return -1; // Retourne -1 si le produit n'est pas disponible
    }

    public void sendReplyToUser(AID userAgent, double productPrice) {
        ACLMessage replyMessage = new ACLMessage(ACLMessage.INFORM);
        replyMessage.addReceiver(userAgent);
        replyMessage.setConversationId("Ask-Product-Price");
        try {
            replyMessage.setContentObject(productPrice);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        send(replyMessage);
    }

    private boolean removeSoldProduct(ProductType soldProduct) {
        return products.removeIf(ProductType -> Objects.equals(ProductType, soldProduct));
    }

}

