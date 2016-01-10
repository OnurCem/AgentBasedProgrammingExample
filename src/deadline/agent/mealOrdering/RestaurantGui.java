package deadline.agent.mealOrdering;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

class RestaurantGui extends JFrame {	
	private RestaurantAgent myAgent;
	
	private JTextField dateField, mealField;
	
	RestaurantGui(RestaurantAgent a) {
		super(a.getLocalName());
		
		myAgent = a;
		
		JPanel p = new JPanel();
		p.setLayout(new GridLayout(2, 2));
		p.add(new JLabel("Meal Date:"));
		dateField = new JTextField(15);
		p.add(dateField);
		p.add(new JLabel("Meal:"));
		mealField = new JTextField(30);
		p.add(mealField);
		getContentPane().add(p, BorderLayout.CENTER);
		
		JButton addButton = new JButton("Add");
		addButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				try {
					String mealDate = dateField.getText().trim();
					String meal = mealField.getText().trim();
					myAgent.updateMenu(mealDate, meal);
					dateField.setText("");
					mealField.setText("");
				}
				catch (Exception ex) {
					JOptionPane.showMessageDialog(RestaurantGui.this, "Invalid values. " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				}
			}
		} );
		p = new JPanel();
		p.add(addButton);
		getContentPane().add(p, BorderLayout.SOUTH);
		
		// Make the agent terminate when the user closes 
		// the GUI using the button on the upper right corner	
		addWindowListener(new	WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				myAgent.doDelete();
			}
		} );
		
		setResizable(false);
	}
	
	public void showGui() {
		pack();
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int centerX = (int)screenSize.getWidth() / 2;
		int centerY = (int)screenSize.getHeight() / 2;
		setLocation(centerX - getWidth() / 2, centerY - getHeight() / 2);
		super.setVisible(true);
	}	
}
