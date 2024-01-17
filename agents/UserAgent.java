package handsOn.circularEconomy.agents;

import handsOn.circularEconomy.data.Part;
import handsOn.circularEconomy.data.Product;
import handsOn.circularEconomy.data.ProductType;
import handsOn.circularEconomy.gui.UserAgentWindow;
import jade.core.AID;
import jade.core.AgentServicesTools;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.gui.GuiAgent;
import jade.gui.GuiEvent;
import jade.lang.acl.UnreadableException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;

import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**class related to the user, owner of products to repair
 * @author emmanueladam
 * */
public class UserAgent extends GuiAgent {
    /**list of products to repair*/
    List<Product> products;
    /**general skill of repairing*/
    int skill;
    /**general budget of repairing*/
    double budget;
    //**general time of repairing*/
    int dayTime;
    LocalDate proposedDate;
            /**gui window*/
    UserAgentWindow window;
    Random hasard = new Random();
    Product productToRepair;
    @Override
    public void setup()
    {
        this.window = new UserAgentWindow(getLocalName(),this);
        window.setButtonActivated(true);
        //add some products choosen randomly in the list Product.getListProducts()
        skill = hasard.nextInt(3);
        products = new ArrayList<>();
        int nbTypeOfProducts = ProductType.values().length;
        int nbPoductsByType = Product.NB_PRODS / nbTypeOfProducts;
        var existingProducts = Product.getListProducts();
        //add products
        for(int i=0; i<nbTypeOfProducts; i++)
            if(hasard.nextBoolean())
               products.add(existingProducts.get(hasard.nextInt(nbPoductsByType) + (i*nbPoductsByType)));
        //we need at least one product
        if(products.isEmpty())  products.add(existingProducts.get(hasard.nextInt(nbPoductsByType*nbTypeOfProducts)));
        window.addProductsToCombo(products);
        println("Here are my objects : ");
        products.forEach(p->println("\t"+p));
    }

    /**the window sends an evt to the agent*/
    @Override
    public void onGuiEvent(GuiEvent evt) {
        if (evt.getType() == UserAgentWindow.OK_EVENT) {
            budget = hasard.nextInt(400-100) + 100;
            dayTime = hasard.nextInt(15-7) + 7;
            println("hello, I have a skill = " + skill);
            println("I have a budget = " + budget + "€");
            println("I have a time = " + dayTime + " jours");
            var partStores = AgentServicesTools.searchAgents(this, "repair", "partstore");
            var coffees = AgentServicesTools.searchAgents(this, "repair", "coffee");
            var distributors = AgentServicesTools.searchAgents(this, "repair", "distributor");
            Product selectedProduct = this.window.getSelectedProduct();
            ProductType selectedProductType = selectedProduct.getType();
            Part partToRepair = selectedProduct.getDefault();
            Integer breakdownLevel = partToRepair.getBreakdownLevel();
            if (breakdownLevel > skill || skill == 0) {
                // Envoyer une requête à chaque agent repair-coffee
                println("J'ai ce niveau de compétence : " + skill + " je n'ai pas trouvé la panne.");
                println("-".repeat(30));
                AID closestRepairCoffee = null;
                LocalDate closestDate = LocalDate.MAX;
                for (AID aid : coffees) {
                    ACLMessage requestMessage = new ACLMessage(ACLMessage.REQUEST);
                    requestMessage.setContent(selectedProductType.toString());
                    requestMessage.addReceiver(aid);
                    requestMessage.setConversationId("Appointment");
                    send(requestMessage);
                    ACLMessage reply = blockingReceive((MessageTemplate.MatchPerformative(ACLMessage.INFORM)));
                    if (reply != null) {
                        String replyContent = reply.getContent();
                        String[] contentParts = replyContent.split(": ");
                        if (contentParts.length == 2) {
                            String repairCoffee = aid.getLocalName();
                            String dateContent = contentParts[1];
                            try {
                                proposedDate = LocalDate.parse(dateContent, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                                println("Repair coffee " + repairCoffee + " proposes a repair date of " + proposedDate);
                                if (proposedDate.isBefore(closestDate)) {
                                    closestDate = proposedDate;
                                    closestRepairCoffee = aid;
                                }
                            } catch (DateTimeParseException e) {
                                println("Invalid date format in the reply from " + repairCoffee);
                            }
                        } else {
                            println("Repair coffee " + aid.getLocalName() + " cannot repair");
                        }
                    }
                }
                if (closestRepairCoffee != null) {
                    println("Selected repair coffee: " + closestRepairCoffee.getLocalName());
                    ACLMessage requestFaultyPart = new ACLMessage(ACLMessage.REQUEST);
                    requestFaultyPart.addReceiver(closestRepairCoffee);
                    requestFaultyPart.setConversationId("Faulty-Part");
                    try {
                        requestFaultyPart.setContentObject(selectedProduct);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    send(requestFaultyPart);
                    ACLMessage replyFaultyPart = blockingReceive(MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchConversationId("Faulty-Part")));
                    if (replyFaultyPart != null) {
                        try {
                            partToRepair = (Part) replyFaultyPart.getContentObject();
                        } catch (UnreadableException e) {
                            throw new RuntimeException(e);
                        }
                        println(partToRepair.getName() + " breakdown level: " + partToRepair.getBreakdownLevel());
                        // Obtenir la date du jour
                        LocalDate currentDate = LocalDate.now();
                        // Calculer la différence en jours entre la date proposée et la date du jour
                        Period period = Period.between(currentDate, proposedDate);
                        // Obtenir la différence en jours
                        long daysDifference = period.getDays();
                        // Soustraire la différence du temps disponible
                        dayTime -= daysDifference;
                        println("Jours restants avant l'abandon : " + dayTime);
                    } else {
                        println("Received null reply for the faulty part");
                    }
                }
            } else {
                println("J'ai les compétences pour détecter la panne moi même et j'ai trouvé cette panne :" + partToRepair);
            }

            if (partToRepair.getBreakdownLevel() < 4) {
                double lowestPartPrice = Double.MAX_VALUE; // Initialiser le prix le plus bas avec une valeur maximale possible
                AID cheapestPartStore = null; // Garder une trace du partStore avec le prix le plus bas
                for (AID aid : partStores) {
                    ACLMessage requestFaultyPartPrice = new ACLMessage(ACLMessage.REQUEST);
                    try {
                        requestFaultyPartPrice.setContentObject(partToRepair);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    requestFaultyPartPrice.addReceiver(aid);
                    requestFaultyPartPrice.setConversationId("Ask-Part-Price");
                    send(requestFaultyPartPrice);
                    ACLMessage replyFaultyPartPrice = blockingReceive((MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchConversationId("Ask-Part-Price"))));
                    if (replyFaultyPartPrice != null) {
                        Double replyContent = null;
                        try {
                            replyContent = (Double) replyFaultyPartPrice.getContentObject();
                        } catch (UnreadableException e) {
                            throw new RuntimeException(e);
                        }
                        String partStore = aid.getLocalName();
                        if (replyContent > 0){
                            println("The part store " + partStore + "  have the part at the price : " + String.format("%.2f",replyContent) + "€");
                            if (replyContent < lowestPartPrice) {
                                lowestPartPrice = replyContent; // Mettre à jour le prix le plus bas
                                cheapestPartStore = aid; // Mettre à jour le partStore avec le prix le plus bas
                            }
                        }
                        else if (replyContent < 0){
                            println("The part store " + partStore + "  don't have the part");
                        }
                    } else {
                        println("Received null reply for the faulty part price from " + aid.getLocalName());
                    }

                }
                if (cheapestPartStore != null) {
                    buyPart(cheapestPartStore, partToRepair);
                    budget = budget - lowestPartPrice;
                    println("J'ai acheté la partie : " + partToRepair.getName() + " à " + cheapestPartStore.getLocalName() + ", il me reste " + String.format("%.2f",budget) + "€");
                }
            } else {
                double lowestProductPrice = Double.MAX_VALUE; // Initialiser le prix le plus bas avec une valeur maximale possible
                AID cheapestDistributor = null; // Garder une trace du partStore avec le prix le plus bas
                for (AID aid : distributors) {
                    ACLMessage requestProductPrice = new ACLMessage(ACLMessage.REQUEST);
                    try {
                        requestProductPrice.setContentObject(selectedProductType);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    requestProductPrice.addReceiver(aid);
                    requestProductPrice.setConversationId("Ask-Product-Price");
                    send(requestProductPrice);
                    ACLMessage replyProductPrice = blockingReceive((MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchConversationId("Ask-Product-Price"))));
                    if (replyProductPrice != null) {
                        Double replyProductContent = null;
                        try {
                            replyProductContent = (Double) replyProductPrice.getContentObject();
                        } catch (UnreadableException e) {
                            throw new RuntimeException(e);
                        }
                        String distributor = aid.getLocalName();
                        if (replyProductContent > 0){
                            println("The distributor " + distributor + "  have the product at the price : " + String.format("%.2f",replyProductContent) + "€");
                            if (replyProductContent < lowestProductPrice) {
                                lowestProductPrice = replyProductContent; // Mettre à jour le prix le plus bas
                                cheapestDistributor = aid; // Mettre à jour le partStore avec le prix le plus bas
                            }
                        }
                        else if (replyProductContent < 0){
                            println("The distributor " + cheapestDistributor + "  don't have the product");
                        }
                    } else {
                        println("Received null reply for the faulty part price from " + aid.getLocalName());
                    }

                }
                if (cheapestDistributor != null) {
                    if (lowestProductPrice < budget) {
                        buyProduct(cheapestDistributor, selectedProductType);
                        budget = budget - lowestProductPrice;
                        println("J'ai acheté le produit complet : " + partToRepair.getName() + " à " + cheapestDistributor.getLocalName() + ", il me reste " + String.format("%.2f",budget) + "€");
                    } else {
                        println("Je n'ai pas le budget pour acheter le produit complet, j'abandonne...");
                    }
                }
            }
        }
        println("-".repeat(30));
    }
    public void println(String s){window.println(s);}

    @Override
    public void takeDown(){println("bye !!!");}

    public void buyPart(AID userAgent, Part part) {
        ACLMessage replyMessage = new ACLMessage(ACLMessage.REQUEST);
        replyMessage.addReceiver(userAgent);
        replyMessage.setConversationId("Buy-Part");
        try {
            replyMessage.setContentObject(part);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        send(replyMessage);
    }

    public void buyProduct(AID userAgent, ProductType product) {
        ACLMessage replyMessage = new ACLMessage(ACLMessage.INFORM);
        replyMessage.addReceiver(userAgent);
        replyMessage.setConversationId("Buy-Product");
        try {
            replyMessage.setContentObject(product);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        send(replyMessage);
    }
}
