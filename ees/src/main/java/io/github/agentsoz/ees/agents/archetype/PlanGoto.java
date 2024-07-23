package io.github.agentsoz.ees.agents.archetype;

/*-
 * #%L
 * Emergency Evacuation Simulator
 * %%
 * Copyright (C) 2014 - 2024 EES code contributors.
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import io.github.agentsoz.bdiabm.v3.AgentNotFoundException;
import io.github.agentsoz.ees.Constants;
import io.github.agentsoz.ees.agents.archetype.ArchetypeAgent.Beliefname;
import io.github.agentsoz.bdiabm.data.ActionContent;
import io.github.agentsoz.jill.lang.Agent;
import io.github.agentsoz.jill.lang.Goal;
import io.github.agentsoz.jill.lang.Plan;
import io.github.agentsoz.jill.lang.PlanStep;
import io.github.agentsoz.util.Location;

import java.util.Map;


public class PlanGoto extends Plan {

	private static final int maximumTries = 3;

	ArchetypeAgent agent = null;
	Constants.EvacActivity destinationType = null;
	private Location destination = null;

	private int tries;

	public PlanGoto(Agent agent, Goal goal, String name) {
		super(agent, goal, name);
		this.agent = (ArchetypeAgent)agent;
		destination = ((GoalGoto)getGoal()).getDestination();
		destinationType = ((GoalGoto)getGoal()).getDestinationType();
		body = steps;
	}

	public boolean context() {
		setName(this.getClass().getSimpleName()); // give this plan a user friendly name for logging purposes
		boolean isStuck = Boolean.valueOf(agent.getBelief(ArchetypeAgent.State.isStuck.name()));
		boolean applicable = !isStuck;
		agent.out("thinks " + getFullName() + " is " + (applicable ? "" : "not ") + "applicable");
		return applicable;
	}

	PlanStep[] steps = {
			() -> {
				agent.out("will do #" + getFullName());

				double distToDest = -1;
				try {
					distToDest = agent.getDrivingDistanceTo(destination);
				} catch (AgentNotFoundException e) {
					agent.handleAgentNotFoundException(e.getMessage());
					drop();
					return;
				}
				// All done if already at the destination,
				// or tried enough times,
				// or were driving but the last bdi action (presumably the drive action) was dropped
				// or is stuck
				if (distToDest <= 0.0 ||
						tries >= maximumTries ||
						(!ActionContent.State.DROPPED.equals(agent.getLastBdiActionState()) &&
								!ActionContent.State.FAILED.equals(agent.getLastBdiActionState())
								&& agent.hasBelief(Beliefname.isDriving.name(), new Boolean(true).toString())) ||
						Boolean.valueOf(agent.getBelief(ArchetypeAgent.State.isStuck.name()))) {
					agent.out("finished driving to "
							+ destination + String.format(" %.0f", distToDest) + "m away"
							+ " after " + tries + " tries"
							+ " #" + getFullName());
					if (distToDest <= 0.0) {
						agent.believe(ArchetypeAgent.State.status.toString(), ArchetypeAgent.StatusValue.at.name() + ":" + destinationType.name());
					} else {
						agent.believe(ArchetypeAgent.State.status.toString(), ArchetypeAgent.StatusValue.at.name() + ":" + Constants.EvacActivity.UnknownPlace.name());
					}
					agent.believe(Beliefname.isDriving.name(), new Boolean(false).toString());
					drop();
					return;
				}
				// Not there yet, so start driving
				agent.out("will start driving to "
						+ destination + String.format(" %.0f", distToDest) + "m away"
						+ " #" + getFullName());
				double replanningActivityDurationInMins = (tries==0) ? ((GoalGoto)getGoal()).getReplanningActivityDurationInMins() : 1;
				Goal action = agent.prepareDrivingGoal(
						destinationType,
						destination,
						((tries==0)) ? Constants.EvacRoutingMode.carFreespeed : Constants.EvacRoutingMode.carGlobalInformation,
						(int)Math.round(replanningActivityDurationInMins));
				tries++;
				agent.believe(ArchetypeAgent.State.status.toString(), ArchetypeAgent.StatusValue.to.name() + ":" + destinationType.name());
				agent.believe(Beliefname.isDriving.name(), new Boolean(action != null).toString());
				subgoal(action); // should be last call in any plan step
			},
			() -> {
				// Must suspend agent in plan step subsequent to starting bdi action
				agent.suspend(true); // should be last call in any plan step
			},
			() -> {
				// reset the plan to the start (loop)
				reset();
			},
	};

	@Override
	public void setPlanVariables(Map<String, Object> vars) {
	}
}
