package deadline.agent.mealOrdering;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.ArrayList;
import java.util.List;

public class MealOrderAgent extends Agent {
    // The date of the meal to buy
    private String targetMealDate;
    // The list of known course and restaurant agents
    private AID[] courseAgents;
    private AID[] restaurantAgents;

    // Agent initializations
    protected void setup() {
        System.out.println("Bonjour! MealOrder-agent " + getAID().getName() + " is ready.");

        // Get the date of the meal to buy as a start-up argument
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            targetMealDate = (String) args[0];
            System.out.println("Target meal date is " + targetMealDate);

            // Add a OneShotBehaviour to make a request to course and restaurant agents
            addBehaviour(new OneShotBehaviour(this) {

                @Override
                public void action() {
                    System.out.println("Trying to buy " + targetMealDate);

                    // Update the list of restaurant agents
                    DFAgentDescription dfAgentDescription = new DFAgentDescription();
                    ServiceDescription serviceDescription = new ServiceDescription();
                    serviceDescription.setType("restaurant");
                    dfAgentDescription.addServices(serviceDescription);

                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, dfAgentDescription);
                        System.out.println("Found the following restaurant agents:");
                        restaurantAgents = new AID[result.length];
                        for (int i = 0; i < result.length; ++i) {
                            restaurantAgents[i] = result[i].getName();
                            System.out.println(restaurantAgents[i].getName());
                        }
                    }
                    catch (FIPAException ex) {
                        ex.printStackTrace();
                    }

                    // Update the list of course agents
                    dfAgentDescription = new DFAgentDescription();
                    serviceDescription = new ServiceDescription();
                    serviceDescription.setType("course");
                    dfAgentDescription.addServices(serviceDescription);

                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, dfAgentDescription);
                        System.out.println("Found the following course agents:");
                        courseAgents = new AID[result.length];
                        for (int i = 0; i < result.length; ++i) {
                            courseAgents[i] = result[i].getName();
                            System.out.println(courseAgents[i].getName());
                        }
                    }
                    catch (FIPAException ex) {
                        ex.printStackTrace();
                    }

                    // Perform the request
                    myAgent.addBehaviour(new RequestPerformer());
                }
            });
        }
        else {
            // Make the agent terminate
            System.out.println("No target meal date specified");
            doDelete();
        }
    }

    // Put agent clean-up operations here
    protected void takeDown() {
        // Printout a dismissal message
        System.out.println("MealOrder-agent " + getAID().getName() + " terminating.");
    }

    private class RequestPerformer extends Behaviour {
        private AID bestRestaurant; // The agent who provides the best meal
        private String bestMeal;  // The best provided meal
        private int repliesCount = 0; // The counter of replies from agents
        private MessageTemplate courseInformationMessageTemplate; // The template to receive replies from course agents
        private MessageTemplate mealInformationMessageTemplate; // The template to receive replies from restaurant agents
        private String courseName; // The name of the course that is found for target date. This is used for storing the reply of course agents.
        private String mealName; // The name of the meal that is found for target date. This is used for storing the reply of restaurant agents.
        private int step = 0;

        public void action() {
            switch (step) {
                case 0:
                    // Send the CFP to all course schedule agents
                    ACLMessage courseInformationCFP = new ACLMessage(ACLMessage.CFP);
                    for (int i = 0; i < courseAgents.length; ++i) {
                        courseInformationCFP.addReceiver(courseAgents[i]);
                    }
                    courseInformationCFP.setContent(targetMealDate);
                    courseInformationCFP.setConversationId("course-information");
                    courseInformationCFP.setReplyWith("courseInformationCFP" + System.currentTimeMillis()); // Unique value
                    myAgent.send(courseInformationCFP);
                    // Prepare the template to get proposals
                    courseInformationMessageTemplate = MessageTemplate.and(MessageTemplate.MatchConversationId("course-information"),
                            MessageTemplate.MatchInReplyTo(courseInformationCFP.getReplyWith()));

                    step = 1;
                    break;

                case 1:
                    // Send the CFP to all restaurant agents
                    ACLMessage mealInformationCFP = new ACLMessage(ACLMessage.CFP);
                    for (int i = 0; i < restaurantAgents.length; ++i) {
                        mealInformationCFP.addReceiver(restaurantAgents[i]);
                    }
                    mealInformationCFP.setContent(targetMealDate);
                    mealInformationCFP.setConversationId("meal-information");
                    mealInformationCFP.setReplyWith("mealInformationCFP" + System.currentTimeMillis()); // Unique value
                    myAgent.send(mealInformationCFP);
                    // Prepare the template to get proposals
                    mealInformationMessageTemplate = MessageTemplate.and(MessageTemplate.MatchConversationId("meal-information"),
                            MessageTemplate.MatchInReplyTo(mealInformationCFP.getReplyWith()));

                    step = 2;
                    break;

                case 2:
                    // Receive all proposals/refusals from course schedule agents
                    ACLMessage courseInformationReply = myAgent.receive(courseInformationMessageTemplate);
                    if (courseInformationReply != null) {
                        // Reply received
                        if (courseInformationReply.getPerformative() == ACLMessage.PROPOSE) {
                            // There is a course on target date
                            courseName = courseInformationReply.getContent();
                        }

                        repliesCount++;
                        if (repliesCount >= courseAgents.length) {
                            // We received all replies
                            step = 3;
                        }
                    } else {
                        block();
                    }
                    break;

                case 3:
                    // Receive all proposals/refusals from restaurant agents
                    ACLMessage mealInformationReply = myAgent.receive(mealInformationMessageTemplate);
                    if (mealInformationReply != null) {
                        // Reply received
                        if (mealInformationReply.getPerformative() == ACLMessage.PROPOSE) {
                            // This is a meal for target date
                            mealName = mealInformationReply.getContent();

                            if (isEligableMealForTargetDate(courseName, mealName)) {
                                // This is the best meal at present
                                bestMeal = mealName;
                                bestRestaurant = mealInformationReply.getSender();
                            }
                        }

                        repliesCount++;
                        if (repliesCount >= restaurantAgents.length) {
                            // We received all replies
                            step = 4;
                        }
                    } else {
                        block();
                    }
                    break;

                case 4:
                    // Send the meal order to the restaurant that provided the best meal
                    ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    order.addReceiver(bestRestaurant);
                    order.setContent(targetMealDate);
                    order.setConversationId("meal-ordering");
                    order.setReplyWith("order" + System.currentTimeMillis());
                    myAgent.send(order);
                    // Prepare the template to get the meal order mealInformationReply
                    mealInformationMessageTemplate = MessageTemplate.and(MessageTemplate.MatchConversationId("meal-ordering"),
                            MessageTemplate.MatchInReplyTo(order.getReplyWith()));
                    step = 5;
                    break;

                case 5:
                    // Receive the meal order mealInformationReply
                    mealInformationReply = myAgent.receive(mealInformationMessageTemplate);
                    if (mealInformationReply != null) {
                        // Meal order mealInformationReply received
                        if (mealInformationReply.getPerformative() == ACLMessage.INFORM) {
                            // Order successful. We can terminate
                            System.out.println("Meal successfully ordered from agent "
                                    + mealInformationReply.getSender().getName() + " for date " + targetMealDate);
                            System.out.println("Meal: " + bestMeal);
                            myAgent.doDelete();
                        }
                        else {
                            System.out.println("Attempt failed: requested meal not available.");
                        }

                        step = 6;
                    } else {
                        block();
                    }
                    break;
            }
        }

        public boolean done() {
            if (step == 4 && bestRestaurant == null) {
                System.out.println("Attempt failed: There is no favorite meal or course on " + targetMealDate);
            }
            return ((step == 4 && bestRestaurant == null) || step == 6);
        }
    }

    /**
     * Checks the course and meal for target date. It returns true if there is a course on that date
     * and the meal is a favorite meal.
     * @param courseName Name of the course
     * @param mealName Name of the meal
     * @return
     */
    private boolean isEligableMealForTargetDate(String courseName, String mealName) {
        List<String> unfavoredMeals = new ArrayList<String>();
        unfavoredMeals.add("fish");
        unfavoredMeals.add("chicken");

        // Check course and meal
        if (courseName != null) {
            if (!unfavoredMeals.contains(mealName)) {
                return true;
            }
        }

        return false;
    }
}
