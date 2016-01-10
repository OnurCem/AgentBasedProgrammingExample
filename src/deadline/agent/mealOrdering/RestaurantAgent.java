package deadline.agent.mealOrdering;

import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;

public class RestaurantAgent extends Agent {
	// The menu of meals for order (maps the date of a meal to its name)
	private Hashtable menu;
	// The GUI by means of which the user can add meals in the menu
	private RestaurantGui myGui;

	// Agent initializations
	protected void setup() {
		// Create the menu
		menu = new Hashtable();

		// Create and show the GUI 
		myGui = new RestaurantGui(this);
		myGui.showGui();

		// Register the meal-selling service in the yellow pages
		DFAgentDescription dfAgentDescription = new DFAgentDescription();
		dfAgentDescription.setName(getAID());
		ServiceDescription serviceDescription = new ServiceDescription();
		serviceDescription.setType("restaurant");
		serviceDescription.setName("JADE-meal-ordering");
		dfAgentDescription.addServices(serviceDescription);
		try {
			DFService.register(this, dfAgentDescription);
		}
		catch (FIPAException ex) {
			ex.printStackTrace();
		}

		// Add the behaviour serving queries from meal order agents
		addBehaviour(new InfoRequestsServer());

		// Add the behaviour serving purchase orders from meal order agents
		addBehaviour(new PurchaseOrdersServer());
	}

	// Put agent clean-up operations here
	protected void takeDown() {
		// Unregister from the yellow pages
		try {
			DFService.deregister(this);
		}
		catch (FIPAException ex) {
			ex.printStackTrace();
		}

		// Close the GUI
		myGui.dispose();
		// Printout a dismissal message
		System.out.println("Restaurant-agent " + getAID().getName() + " terminating.");
	}

	/**
     This is invoked by the GUI when the user adds a new meal for order
	 */
	public void updateMenu(final String mealDate, final String meal) {
		addBehaviour(new OneShotBehaviour() {
			public void action() {
				menu.put(mealDate, meal);
				System.out.println("Added " + meal + " on date " + mealDate);
			}
		} );
	}

	/**
	   Inner class InfoRequestsServer.
	   This is the behaviour used by Restaurant agents to serve incoming requests
	   for information from MealOrder agents.
	   If the requested meal is in the local menu the restaurant agent replies
	   with a PROPOSE message specifying the meal name. Otherwise a REFUSE message is
	   sent back.
	 */
	private class InfoRequestsServer extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// CFP Message received. Process it
				String mealDate = msg.getContent();
				ACLMessage reply = msg.createReply();

				String meal =(String) menu.get(mealDate);
				if (meal != null) {
					// The requested meal is available for order. Reply with the meal name
					reply.setPerformative(ACLMessage.PROPOSE);
					reply.setContent(String.valueOf(meal));
				}
				else {
					// The requested meal is NOT available for order
					reply.setPerformative(ACLMessage.REFUSE);
					reply.setContent("not-available");
				}
				myAgent.send(reply);
			}
			else {
				block();
			}
		}
	}  // End of inner class InfoRequestsServer

	/**
	   Inner class PurchaseOrdersServer.
	   This is the behaviour used by Restaurant agents to serve incoming
	   order acceptances from MealOrder agents.
	   The restaurant agent replies with an INFORM message to notify the MealOrder agent that the
	   order has been successfully completed.
	 */
	private class PurchaseOrdersServer extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// ACCEPT_PROPOSAL Message received. Process it
				String mealDate = msg.getContent();
				ACLMessage reply = msg.createReply();

				String meal = (String) menu.get(mealDate);
				if (meal != null) {
					reply.setPerformative(ACLMessage.INFORM);
					System.out.println(meal + ", " + "ordered to agent " + msg.getSender().getName()
                            + " for date " + mealDate);
				}
				else {
					// The requested meal has been ordered to another agent in the meanwhile .
					reply.setPerformative(ACLMessage.FAILURE);
					reply.setContent("not-available");
				}
				myAgent.send(reply);
			}
			else {
				block();
			}
		}
	}  // End of inner class InfoRequestsServer
}
