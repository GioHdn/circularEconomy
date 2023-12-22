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
    int budget;
    /**gui window*/
    UserAgentWindow window;

    Product productToRepair;
    @Override
    public void setup()
    {
        this.window = new UserAgentWindow(getLocalName(),this);
        window.setButtonActivated(true);
        //add a random skill
        Random hasard = new Random();
        skill = hasard.nextInt(1);
        budget = hasard.nextInt(100-50) + 50;
        println("hello, I have a skill = " + skill);
        println("I have a budget = " + budget);
        //add some products choosen randomly in the list Product.getListProducts()
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
            var partStores = AgentServicesTools.searchAgents(this, "repair", "partstore");
            var coffees = AgentServicesTools.searchAgents(this, "repair", "coffee");
            Product selectedProduct = this.window.getSelectedProduct();
            ProductType selectedProductType = selectedProduct.getType();
            Part partToRepair = selectedProduct.getDefault();
            Integer breakdownLevel = partToRepair.getbreakdownLevel();
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
                                LocalDate proposedDate = LocalDate.parse(dateContent, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
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
                        println(partToRepair.getName());
                    } else {
                        println("Received null reply for the faulty part");
                    }
                }
            } else {
                println("J'ai les compétences pour détecter la panne moi même et j'ai trouvé cette panne :" + partToRepair);
            }

            if (partToRepair.getbreakdownLevel() < 4) {
                for (AID aid : partStores) {
                    ACLMessage requestFaultyPartPrice = new ACLMessage(ACLMessage.REQUEST);
                    try {
                        requestFaultyPartPrice.setContentObject(partToRepair);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    requestFaultyPartPrice.addReceiver(aid);
                    requestFaultyPartPrice.setConversationId("Ask-Price");
                    send(requestFaultyPartPrice);
                    ACLMessage replyFaultyPartPrice = blockingReceive((MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchConversationId("Ask-Price"))));
                    if (replyFaultyPartPrice != null) {
                        String replyContent = replyFaultyPartPrice.getContent();
                        String partStore = aid.getLocalName();
                        println(partStore + " " + replyContent);
                    } else {
                        println("Received null reply for the faulty part price from " + aid.getLocalName());
                    }
                }
            } else {
                println("l'objet est définitivement cassé");
            }
        }
        println("-".repeat(30));
    }
    public void println(String s){window.println(s);}

    @Override
    public void takeDown(){println("bye !!!");}
}
