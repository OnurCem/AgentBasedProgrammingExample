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

public class CourseScheduleAgent extends Agent {
	// The list of courses in the schedule (maps the date of a course to its name)
	private Hashtable courseList;
	// The GUI by means of which the user can add books in the catalogue
	private CourseGui myGui;

	// Agent initializations
	protected void setup() {
		// Create the list
		courseList = new Hashtable();

		// Create and show the GUI 
		myGui = new CourseGui(this);
		myGui.showGui();

		// Register the course scheduling service in the yellow pages
		DFAgentDescription dfAgentDescription = new DFAgentDescription();
		dfAgentDescription.setName(getAID());
		ServiceDescription serviceDescription = new ServiceDescription();
		serviceDescription.setType("course");
		serviceDescription.setName("JADE-course-scheduling");
		dfAgentDescription.addServices(serviceDescription);
		try {
			DFService.register(this, dfAgentDescription);
		}
		catch (FIPAException ex) {
			ex.printStackTrace();
		}

		// Add the behaviour serving queries from buyer agents
		addBehaviour(new InfoRequestsServer());
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
		System.out.println("Course-agent " + getAID().getName() + " terminating.");
	}

	/**
     This is invoked by the GUI when the user adds a new course to schedule
	 */
	public void updateCourse(final String courseDate, final String courseName) {
		addBehaviour(new OneShotBehaviour() {
			public void action() {
				courseList.put(courseDate, courseName);
				System.out.println("Added course " + courseName + " on " + courseDate + " to schedule");
			}
		} );
	}

	/**
	   Inner class InfoRequestsServer.
	   This is the behaviour used by CourseSchedule-agents to serve incoming requests
	   for information from MealOrder agents.
	   If there is a course on the requested date agent replies
	   with a PROPOSE message specifying the course name. Otherwise a REFUSE message is
	   sent back.
	 */
	private class InfoRequestsServer extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// CFP Message received. Process it
				String courseDate = msg.getContent();
				ACLMessage reply = msg.createReply();

				String courseName = (String) courseList.get(courseDate);
				if (courseName != null) {
					// There is a course for the requested date. Reply with the course name
					reply.setPerformative(ACLMessage.PROPOSE);
					reply.setContent(String.valueOf(courseName));
				}
				else {
					// There is no course for the requested date
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
}
