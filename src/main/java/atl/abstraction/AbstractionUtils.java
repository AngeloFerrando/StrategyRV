package atl.abstraction;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import atl.abstraction.beans.*;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

public class AbstractionUtils {

	private static final String MODEL_JSON_FILE_NAME = "model.json";
	private final static Log logger = LogFactory.getLog(AbstractionUtils.class);
	public static String mcmas;

	public static List<StateCluster> getStateClusters(AtlModel atlModel) {
		List<StateCluster> stateClusters = new ArrayList<>();
		for (State state : atlModel.getStates()) {
			stateClusters.add(state.toStateCluster());
		}

		for (Agent agent : atlModel.getAgents()) {
			for (List<String> indistinguishableStateNameList : agent.getIndistinguishableStates()) {
				for (StateCluster stateCluster : stateClusters) {
					List<State> indistinguishableStateList =
							indistinguishableStateNameList.parallelStream()
									.map(stateName->atlModel.getState(stateName)).collect(Collectors.toList());
					if (stateCluster.containsAnyChildState(indistinguishableStateList)) {
						for (State state : indistinguishableStateList) {
							if (!stateCluster.containsChildState(state)) {
								stateCluster.addChildState(state);
							}
						}
					}
				}
			}
		}

		for (StateCluster stateCluster1 : stateClusters) {
			for (StateCluster stateCluster2 : stateClusters) {
				if (stateCluster1.containsAnyChildState(stateCluster2)) {
					stateCluster1.addChildStates(stateCluster2);
				}
			}
		}

		return stateClusters.stream().distinct().collect(Collectors.toList());
	}



	public static List<Transition> getMayTransitions(final AtlModel atlModel, final List<StateCluster> stateClusters) {
		List<Transition> transitions = new ArrayList<>();
		for (StateCluster fromStateCluster : stateClusters) {
			for (StateCluster toStateCluster : stateClusters) {
				List<List<AgentAction>> agentActions = fromStateCluster.hasMayTransition(toStateCluster, atlModel);
				if (!agentActions.isEmpty()) {
					removeDuplicates(agentActions);
					Transition transition = new Transition();
					transition.setFromState(fromStateCluster.getName());
					transition.setToState(toStateCluster.getName());
					transition.setAgentActions(agentActions);
					transitions.add(transition);
				}
			}
		}

		return transitions;
	}

	public static List<Transition> getMustTransitions(final AtlModel atlModel, final List<StateCluster> stateClusters) {
		List<Transition> transitions = new ArrayList<>();
		for (StateCluster fromStateCluster : stateClusters) {
			for (StateCluster toStateCluster : stateClusters) {
				List<List<AgentAction>> agentActions = fromStateCluster.hasMustTransition(toStateCluster, atlModel);
				if (!agentActions.isEmpty()) {
					removeDuplicates(agentActions);
					Transition transition = new Transition();
					transition.setFromState(fromStateCluster.getName());
					transition.setToState(toStateCluster.getName());
					transition.setAgentActions(agentActions);
					transitions.add(transition);
				}
			}
		}

		return transitions;
	}

	public static void removeDuplicates(List<List<AgentAction>> agentActions) {
		Map<String, List<AgentAction>> actionMap = new HashMap<>();
		for (List<AgentAction> actionList : agentActions) {
			actionMap.put(actionList.toString(), actionList);
		}
		agentActions.clear();
		agentActions.addAll(actionMap.values());
	}

	public static String generateDotGraph(AtlModel atlModel) {
		StringBuilder stringBuilder = new StringBuilder("digraph G {").append(System.lineSeparator());
		//stringBuilder.append("size=\"10,5\"\nratio=compress\n");
		List<Transition> transitions = atlModel.getTransitions();
		for (Transition transition : transitions) {
			if (CollectionUtils.isEmpty(atlModel.getState(transition.getFromState()).getLabels())) {
				stringBuilder.append(transition.getFromState());
			} else {
				stringBuilder
						.append("\"").append(transition.getFromState()).append("(").append(String.join(", ", atlModel.getState(transition.getFromState()).getLabels())).append(")\"");
			}
			stringBuilder.append("->");
			if (CollectionUtils.isEmpty(atlModel.getState(transition.getToState()).getLabels())) {
				stringBuilder.append(transition.getToState());
			} else {
				stringBuilder
						.append("\"").append(transition.getToState()).append("(").append(String.join(", ", atlModel.getState(transition.getToState()).getLabels())).append(")\"");
			}
			List<String> list1 = new ArrayList<>();
			for(List<AgentAction> agentActionList: transition.getAgentActions()) {
				List<String> list2 = new ArrayList<>();
				for (AgentAction agentAction : agentActionList) {
					list2.add(agentAction.getAgent()+ "." +agentAction.getAction());
				}
				list1.add(MessageFormat.format("({0})", String.join(",", list2)));
			}
			stringBuilder.append("[ label = \"" + String.join("\\n", list1) + "\" ];").append(System.lineSeparator());
		}
		stringBuilder.append("}").append(System.lineSeparator());
		return stringBuilder.toString();
	}

	public static void validateAtlModel(AtlModel atlModel) throws Exception {
		validateTransitions(atlModel);
		validateGroup(atlModel);
	}

	private static void validateTransitions(AtlModel atlModel) throws Exception {
		for(Transition transition : atlModel.getTransitions()) {
			if (!atlModel.getStateMap().containsKey(transition.getFromState())) {
				throw new Exception(MessageFormat.format("invalid state {0} in transition : {1} {2}",
						transition.getFromState(), System.lineSeparator(), transition));
			}
			if (!atlModel.getStateMap().containsKey(transition.getToState())) {
				throw new Exception(MessageFormat.format("invalid state {0} in transition : {1} {2}",
						transition.getToState(), System.lineSeparator(), transition));
			}
			if (transition.isDefaultTransition() && CollectionUtils.isNotEmpty(transition.getAgentActions())) {
				throw new Exception(MessageFormat.format("The transition cannot be a default one and have explicit agent actions : {0} {1}",
						System.lineSeparator(), transition));
			}
			for(List<AgentAction> agentActionList : transition.getAgentActions()) {
				validateAgentActionList(atlModel, transition, agentActionList);
			}
		}
	}

	private static void validateAgentActionList(AtlModel atlModel, Transition transition, List<AgentAction> agentActionList) throws Exception {
		for (AgentAction agentAction : agentActionList) {
			validateAgentAction(atlModel, transition, agentAction);
		}
		List<String> agents = agentActionList.parallelStream().map(AgentAction::getAgent).collect(Collectors.toList());
		Collection<String> agentNotDefinedList = CollectionUtils.subtract(agents, atlModel.getAgentMap().keySet());
		if (CollectionUtils.isNotEmpty(agentNotDefinedList)) {
			throw new Exception (MessageFormat.format("Some agents have not been defined : {0} for the transition : {1} {2}",
					agentNotDefinedList, System.lineSeparator(), transition));
		}
		Collection<String> missingAgentActionsList = CollectionUtils.subtract(atlModel.getAgentMap().keySet(), agents);
		if (CollectionUtils.isNotEmpty(missingAgentActionsList)) {
			throw new Exception (MessageFormat.format("Some agent actions have not been defined : {0} for the transition : {1} {2}",
					missingAgentActionsList, System.lineSeparator(), transition));
		}
	}

	private static void validateAgentAction(AtlModel atlModel, Transition transition, AgentAction agentAction) throws Exception {
		if (!atlModel.getAgentMap().containsKey(agentAction.getAgent())) {
			throw new Exception (MessageFormat.format("Invalid agent {0} in agentAction : {1} for the transition : {2} {3}",
					agentAction.getAgent(), agentAction, System.lineSeparator(), transition));
		}
		Agent agent = atlModel.getAgentMap().get(agentAction.getAgent());
		if (!agent.getActions().contains(agentAction.getAction())) {
			throw new Exception (MessageFormat.format("Invalid action {0} in agentAction : {1} for the transition : {2} {3}",
					agentAction.getAction(), agentAction, System.lineSeparator(), transition));
		}
	}

	private static void validateGroup(AtlModel atlModel) throws Exception {
		List<String> groupAgents = atlModel.getGroup().getAgents();
		Collection<String> agentNotDefinedList = CollectionUtils.subtract(groupAgents, atlModel.getAgentMap().keySet());
		if (CollectionUtils.isNotEmpty(agentNotDefinedList)) {
			throw new Exception (MessageFormat.format("Some agents in the group have not been defined : {0}",
					agentNotDefinedList, System.lineSeparator(), atlModel));
		}
	}


    /*
    public static String readSampleFile() {
        try {
            File sampleFile = new ClassPathResource(MODEL_JSON_FILE_NAME).getFile();
            return new String(FileUtils.readFileToByteArray(sampleFile));
        } catch (IOException ioe) {
            logger.error("Error while trying to read the sample file.", ioe);
        }

        return null;
    }
    */

	public static void processDefaultTransitions(AtlModel atlModel) throws Exception {
		for(Entry<String, List<Transition>> entry : atlModel.getTransitionMap().entrySet()) {
			List<Transition> transitions = entry.getValue();
			List<Transition> defaultTransitions = transitions.parallelStream().filter(transition -> transition.isDefaultTransition()).collect(Collectors.toList());
			if (CollectionUtils.isEmpty(defaultTransitions)) {
				continue;
			}
			if (defaultTransitions.size() > 1) {
				throw new Exception (MessageFormat.format("The state {0} has {1} default transition, only one is allowed. Transitions : {2} {3}",
						entry.getKey(), defaultTransitions.size(), System.lineSeparator(), defaultTransitions));
			}
			Collection<Transition> explicitTransitions = CollectionUtils.subtract(transitions, defaultTransitions);
			List<List<List<AgentAction>>> existingActionLists = explicitTransitions.parallelStream().map(Transition::getAgentActions).collect(Collectors.toList());
			Set<List<AgentAction>> actions = defaultTransitions.get(0).getMultipleAgentActions()
					.parallelStream()
					.map(
							multipleAgentAction->multipleAgentAction.getActions()
									.stream()
									.map(action -> new AgentAction(multipleAgentAction.getAgent(), action))
									.collect(Collectors.toList()))
					.collect(Collectors.toSet());
			List<List<AgentAction>> possibleActions = Lists.cartesianProduct(actions.toArray(new ArrayList<?>[actions.size()])).parallelStream().map(list->list.stream().map(action->(AgentAction) action).collect(Collectors.toList())).collect(Collectors.toList());
			for (List<List<AgentAction>> agentActionsList : existingActionLists) {
				for (List<AgentAction> agentActionList : agentActionsList) {
					Iterator<List<AgentAction>> iterator = possibleActions.iterator();
					while (iterator.hasNext()) {
						if (CollectionUtils.isEqualCollection(iterator.next(), agentActionList)) {
							iterator.remove();
						}
					}
				}
			}
			atlModel.getTransitions().add(new Transition(entry.getKey(), defaultTransitions.get(0).getToState(), possibleActions));
			atlModel.setTransitions(Lists.newLinkedList(CollectionUtils.removeAll(atlModel.getTransitions(), defaultTransitions)));
		}
	}


	public static String generateMCMASProgram(AtlModel atlModel) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(System.lineSeparator()).append("Agent Environment").append(System.lineSeparator());
		stringBuilder.append("\t").append("Vars :").append(System.lineSeparator());
		for (State state: atlModel.getStates()) {
			stringBuilder.append("\t").append("\t").append(state.getName()).append(" : boolean;").append(System.lineSeparator());
			for (String label: state.getLabels()) {
				stringBuilder.append("\t").append("\t").append(label).append(" : boolean;").append(System.lineSeparator());
			}
			for (String label: state.getFalseLabels()) {
				stringBuilder.append("\t").append("\t").append(label).append(" : boolean;").append(System.lineSeparator());
			}
		}
		stringBuilder.append("\t").append("end Vars").append(System.lineSeparator());
		stringBuilder.append("\t").append("Actions = {};").append(System.lineSeparator());
		stringBuilder.append("\t").append("Protocol :").append(System.lineSeparator());
		stringBuilder.append("\t").append("end Protocol").append(System.lineSeparator());
		stringBuilder.append("\t").append("Evolution :").append(System.lineSeparator());
		for (Transition transition: atlModel.getTransitions()) {
			State toState = atlModel.getState(transition.getToState());
			State fromState = atlModel.getState(transition.getFromState());
			stringBuilder.append("\t").append("\t");
			if (!toState.equals(fromState)) {
				stringBuilder.append(fromState.getName()).append(" = false ");
				if (CollectionUtils.isNotEmpty(fromState.getLabels())) {
					for (String label: fromState.getLabels()) {
						stringBuilder.append("and ").append(label).append(" = false ");
					}
				}
				stringBuilder.append("and ");
			}

			stringBuilder.append(toState.getName()).append(" = true ");
			if (CollectionUtils.isNotEmpty(toState.getLabels())) {
				for (String label : toState.getLabels()) {
					stringBuilder.append("and ").append(label).append(" = true ");
				}
			}
			stringBuilder.append(" if ");

			stringBuilder.append(fromState.getName()).append(" = true ");
			if (CollectionUtils.isNotEmpty(fromState.getLabels())) {
				for (String label: fromState.getLabels()) {
					stringBuilder.append("and ").append(label).append(" = true ");
				}
			}

			if (!toState.equals(fromState)) {
				stringBuilder.append("and ").append(toState.getName()).append(" = false ");
				if (CollectionUtils.isNotEmpty(toState.getLabels())) {
					for (String label : toState.getLabels()) {
						stringBuilder.append("and ").append(label).append(" = false ");
					}
				}
			}

			stringBuilder.append("and ");
			if (transition.getAgentActions().size()>1)
				stringBuilder.append("(");
			for (int i = 0; i < transition.getAgentActions().size(); i++) {
				List<AgentAction> agentActionList = transition.getAgentActions().get(i);
				stringBuilder.append("(");
				for (int j = 0; j < agentActionList.size(); j++) {
					AgentAction agentAction = agentActionList.get(j);
					stringBuilder.append(agentAction.getAgent()).append(".Action").append(" = ").append(agentAction.getAction());
					if (j<agentActionList.size()-1)
						stringBuilder.append(" and ");
				}
				stringBuilder.append(")");
				if (i<transition.getAgentActions().size()-1)
					stringBuilder.append(" or ").append(System.lineSeparator()).append("\t\t\t\t\t");
			}
			if (transition.getAgentActions().size()>1)
				stringBuilder.append(")");

			if (transition.getMultipleAgentActions().size()>1)
				stringBuilder.append("(");
			for (int i = 0; i < transition.getMultipleAgentActions().size(); i++) {
				MultipleAgentAction multiAction = transition.getMultipleAgentActions().get(i);
				stringBuilder.append("(");
				for (int j = 0; j < multiAction.getActions().size(); j++) {
					String agentAction = multiAction.getActions().get(j);
					stringBuilder.append(multiAction.getAgent()).append(".Action").append(" = ").append(agentAction);
					if (j<multiAction.getActions().size()-1)
						stringBuilder.append(" or ");
				}
				stringBuilder.append(")");
				if (i<transition.getMultipleAgentActions().size()-1)
					stringBuilder.append(" or ").append(System.lineSeparator()).append("\t\t\t\t\t");
			}
			if (transition.getMultipleAgentActions().size()>1)
				stringBuilder.append(")");

			stringBuilder.append(";").append(System.lineSeparator());
		}
		stringBuilder.append("\t").append("end Evolution").append(System.lineSeparator());
		stringBuilder.append("end Agent").append(System.lineSeparator());

		for (Agent agent:atlModel.getAgents()) {
			stringBuilder.append("Agent ").append(agent.getName()).append(System.lineSeparator());
			List<String> lobsvars = new ArrayList<>();
			for (State state : atlModel.getStates()) {
				lobsvars.add(state.getName());
				for (String label : state.getLabels())
					lobsvars.add(label);
			}
			stringBuilder.append("\t").append("Lobsvars = {").append(String.join(", ", lobsvars)).append("};").append(System.lineSeparator());
			stringBuilder.append("\t").append("Vars : ").append(System.lineSeparator());
			stringBuilder.append("\t").append("\t").append("play : boolean;").append(System.lineSeparator());
			stringBuilder.append("\t").append("end Vars").append(System.lineSeparator());
			stringBuilder.append("\t").append("Actions = {").append(String.join(",", agent.getActions())).append("};");
			Map<String, List<String>> availableActionMap = getAvailableActions(atlModel, agent);
			stringBuilder.append(System.lineSeparator()).append("\t").append("Protocol : ").append(System.lineSeparator());
			for (Entry<String, List<String>> availableActionsEntry: availableActionMap.entrySet()) {
				stringBuilder.append("\t").append("\t").append("Environment.")
						.append(availableActionsEntry.getKey()).append(" = true");
				State state = atlModel.getState(availableActionsEntry.getKey());
				if (CollectionUtils.isNotEmpty(state.getLabels())) {
					for (String label : state.getLabels())
						stringBuilder.append(" and ").append("Environment.").append(label).append(" = true");
				}
				stringBuilder.append(" : {")
						.append(String.join(",", availableActionsEntry.getValue())).append("};").append(System.lineSeparator());
			}
			stringBuilder.append("\t").append("end Protocol").append(System.lineSeparator());
			stringBuilder.append("\t").append("Evolution : ").append(System.lineSeparator());
			stringBuilder.append("\t").append("\t").append("play = true if play = true;").append(System.lineSeparator());
			stringBuilder.append("\t").append("end Evolution").append(System.lineSeparator());
			stringBuilder.append("end Agent").append(System.lineSeparator());
		}

		stringBuilder.append("Evaluation").append(System.lineSeparator());
		for (String term: atlModel.getFormula().getTerms()) {
			stringBuilder.append("\t").append(term).append(" if (Environment.").append(term).append(" = true);").append(System.lineSeparator());
		}
		stringBuilder.append("\t").append("end Evaluation").append(System.lineSeparator());

		stringBuilder.append("\t").append("InitStates").append(System.lineSeparator());
		for (int i = 0; i < atlModel.getStates().size(); i++) {
			State state = atlModel.getStates().get(i);
			stringBuilder.append("\t").append("\t").append("Environment.").append(state.getName()).append(" = ").append(state.isInitial());
			if (CollectionUtils.isNotEmpty(state.getLabels())) {
				stringBuilder.append(" and ").append(System.lineSeparator());
				for (int j = 0; j < state.getLabels().size(); j++) {
					String label = state.getLabels().get(j);
					stringBuilder.append("\t").append("\t").append("Environment.").append(label).append(" = ").append(state.isInitial());
					if (j<state.getLabels().size()-1)
						stringBuilder.append(" and ").append(System.lineSeparator());
				}
			}

			if (CollectionUtils.isNotEmpty(state.getFalseLabels())) {
				stringBuilder.append(" and ").append(System.lineSeparator());
				for (int j = 0; j < state.getFalseLabels().size(); j++) {
					String label = state.getFalseLabels().get(j);
					stringBuilder.append("\t").append("\t").append("Environment.").append(label).append(" = false");
					if (j<state.getLabels().size()-1)
						stringBuilder.append(" and ").append(System.lineSeparator());
				}
			}

			if (i<atlModel.getStates().size()-1) {
				stringBuilder.append(" and ").append(System.lineSeparator());
			}
		}

		if (CollectionUtils.isNotEmpty(atlModel.getAgents())) {
			stringBuilder.append(" and ").append(System.lineSeparator());
			for (int i = 0; i < atlModel.getAgents().size(); i++) {
				Agent agent = atlModel.getAgents().get(i);
				stringBuilder.append("\t").append("\t").append(agent.getName()).append(".play = true");
				if (i<atlModel.getAgents().size()-1)
					stringBuilder.append(" and ").append(System.lineSeparator());
			}
		}

		stringBuilder.append(";").append(System.lineSeparator()).append("\t").append("end InitStates").append(System.lineSeparator());

		stringBuilder.append("Groups").append(System.lineSeparator());
		stringBuilder.append("\t").append(atlModel.getGroup().getName()).append("=").append("{").append(String.join(",", atlModel.getGroup().getAgents())).append("};").append(System.lineSeparator());
		stringBuilder.append("end Groups").append(System.lineSeparator());

		stringBuilder.append("Formulae").append(System.lineSeparator());
		stringBuilder.append("\t");

		Formula aux = atlModel.getFormula();
		while(aux.getSubformula() != null) {
			if(aux.getName() != null) {
				stringBuilder.append("<").append(aux.getName()).append(">");
			}
			stringBuilder.append(aux.getLTLFormula()).append(aux.getOperator());
			aux = aux.getSubformula();
		}
		if(aux.getName() != null) {
			stringBuilder.append("<").append(aux.getName()).append(">");
		}
		stringBuilder.append(aux.getLTLFormula());
		// stringBuilder.append("<").append(atlModel.getGroup().getName()).append(">").append(atlModel.getFormula().getSubformula());

		stringBuilder.append(";").append(System.lineSeparator());
		stringBuilder.append("end Formulae").append(System.lineSeparator());

		return stringBuilder.toString();
	}


	private static Map<String, List<String>> getAvailableActions(AtlModel atlModel, Agent agent) {
		Map<String, List<String>> availableActionMap = new HashMap<>();
		for (Transition transition : atlModel.getTransitions()) {
			if (!availableActionMap.containsKey(transition.getFromState())) {
				availableActionMap.put(transition.getFromState(), new ArrayList<>());
			}

			for (List<AgentAction> agentActionList : transition.getAgentActions()) {
				for (AgentAction agentAction : agentActionList) {
					if (agentAction.getAgent().equals(agent.getName())
							&&
							!availableActionMap.get(transition.getFromState()).contains(agentAction.getAction())) {
						availableActionMap.get(transition.getFromState()).add(agentAction.getAction());
					}

				}
			}
		}

		return availableActionMap;
	}

	public static String modelCheck_ir(String mcmasFilePath) throws IOException { // -atlk -uniform
		try(Scanner scanner = new Scanner(Runtime.getRuntime().exec(mcmas + "/mcmas -atlk -uniform " + mcmasFilePath).getInputStream()).useDelimiter("\\A")) {
			return scanner.hasNext() ? scanner.next() : "";
		}
	}

	public static String modelCheck_IR(String mcmasFilePath) throws IOException {
		try(Scanner scanner = new Scanner(Runtime.getRuntime().exec(mcmas + "/mcmas " + mcmasFilePath).getInputStream()).useDelimiter("\\A")) {
			return scanner.hasNext() ? scanner.next() : "";
		}
	}

	public static boolean getMcmasResult(String mcmasOutput) {
		return  (mcmasOutput.contains("is TRUE in the model"));
	}

	public static List<AtlModel> allModels(AtlModel model) {
		List<AtlModel> allModels = new ArrayList<>();
		for(int k = 1; k <= model.getStates().size(); k++) {
			Set<Set<State>> combinations = allCombinations(model, k);
			for (Set<State> combinationK : combinations) {
				for (State initialState : combinationK) {
					AtlModel modelK = new AtlModel();
					List<State> auxList = new ArrayList<>();
					for (State stateAux : combinationK) {
						State newState = new State(stateAux.getName(), initialState.equals(stateAux));
						newState.setLabels(new ArrayList<>(stateAux.getLabels()));
						newState.setFalseLabels(new ArrayList<>(stateAux.getFalseLabels()));
						auxList.add(newState);
					}
//                    State sinkState = new State();
//                    sinkState.setName("sink");
//                    auxList.add(sinkState);
					modelK.setStates(auxList);
					List<Agent> agentsAuxList = new ArrayList<>();
					for(Agent agent : model.getAgents()) {
						Agent newAgent = new Agent();
						newAgent.setName(agent.getName());
						newAgent.setActions(new ArrayList<>(agent.getActions()));
						newAgent.setIndistinguishableStates(new ArrayList<>());
						for(List<String> indS : agent.getIndistinguishableStates()) {
							List<String> indSAux = indS.stream().filter(modelK::hasState).collect(Collectors.toList());
							if(indSAux.size() >= 2){
								newAgent.getIndistinguishableStates().add(indSAux);
							}
						}
						agentsAuxList.add(newAgent);
					}
					modelK.setAgents(agentsAuxList);
					modelK.setFormula(null); // will be set using innermost formula in Alg1 and Alg2
					modelK.setGroup(model.getGroup());
					Set<Transition> transitionsK = new HashSet<>();
					boolean firstSink = true;
					for (Transition trans : model.getTransitions()) {
						if (modelK.hasState(trans.getFromState())) {
							if(modelK.hasState(trans.getToState())) {
								transitionsK.add(trans);
							}
							else {
								if(firstSink) {
									State sinkState = new State();
									sinkState.setName("sink");
									auxList.add(sinkState);
									modelK.setStates(auxList);
									firstSink = false;
								}
								Transition trSink = new Transition();
								trSink.setFromState(trans.getFromState());
								trSink.setToState("sink");
                                /*trSink.setAgentActions(new ArrayList<>());
                                for(List<AgentAction> aal : trans.getAgentActions()) {
                                    List<AgentAction> aalAux = new ArrayList<>();
                                    for(AgentAction aa : aal) {
                                        AgentAction newAa = new AgentAction();
                                        newAa.setAgent(aa.getAgent());
                                        newAa.setAction(aa.getAction());
                                        aalAux.add(newAa);
                                    }
                                    trSink.getAgentActions().add(aalAux);
                                }*/
								trSink.setAgentActions(trans.copyAgentActions());
                                /*List<MultipleAgentAction> maalAux = new ArrayList<>();
                                for(MultipleAgentAction maa : trans.getMultipleAgentActions()) {
                                    MultipleAgentAction newMaa = new MultipleAgentAction();
                                    newMaa.setAgent(maa.getAgent());
                                    newMaa.setActions(new ArrayList<>(maa.getActions()));
                                    maalAux.add(newMaa);
                                }
                                trSink.setMultipleAgentActions(maalAux);*/
								trSink.setMultipleAgentActions(trans.copyMultiAgentActions());
								trSink.setDefaultTransition(trans.isDefaultTransition());
								transitionsK.add(trSink);
							}
						}
					}
					modelK.setTransitions(new ArrayList<>(transitionsK));
					modelK.setStateMap(null);
					allModels.add(modelK);
				}
			}
		}
		return new ArrayList<>(allModels);
	}

	private static Set<Set<State>> allCombinations(AtlModel model, int k) {
		Set<Set<State>> groupsOfK = new HashSet<>();
		if(k <= 1) {
			for (State state : model.getStates()) {
				State newState = new State(state.getName(), false);
				newState.setLabels(state.getLabels());
				HashSet<State> aux = new HashSet<>();
				aux.add(newState);
				groupsOfK.add(aux);
			}
		} else {
			for (Set<State> groupOfKMinus1 : allCombinations(model, k - 1)) {
				for (State state : model.getStates()) {
					if (!groupOfKMinus1.contains(state)) {
						Set<State> groupOfK = new HashSet<>();
						for (State stateAux : groupOfKMinus1) {
							State newState = new State(stateAux.getName(), false);
							newState.setLabels(stateAux.getLabels());
							groupOfK.add(newState);
						}
						State newState = new State(state.getName(), false);
						newState.setLabels(state.getLabels());
						groupOfK.add(newState);
						groupsOfK.add(groupOfK);
					}
				}
			}
		}
		return groupsOfK;
	}

	public static List<AtlModel> validateSubModels(AtlModel model, List<AtlModel> candidates, boolean imperfect, boolean silent, HashMap<String, String> mapAtomToFormula) throws Exception {
		int j = 1;
		int i = 0;

		if(!mapAtomToFormula.isEmpty()) {
			i = mapAtomToFormula.size() + 1;
		}

		List<AtlModel> results = new ArrayList<>();
		System.out.println("Start validating all sub-models..");
		for(AtlModel candidate : candidates) {
			//processDefaultTransitions(candidate);
			if(!silent) System.out.println("Checking sub-model " + j++ + " of " + candidates.size());
			if(imperfect && !candidate.isConnected()) {
				continue;
			}

			Formula formula1, formulaAux = model.getFormula().clone();
			boolean satisfied;
			do {
				formula1 = formulaAux.innermostFormula();
				// compile candidate sub-model to ispl
				candidate.setFormula(formula1);
				String mcmasProgram = AbstractionUtils.generateMCMASProgram(candidate);
				// write temporary ispl file
				String fileName = "./tmp/subModel" + System.currentTimeMillis() + ".ispl";
				Files.write(Paths.get(fileName), mcmasProgram.getBytes());
				// model check the ispl model
				if(imperfect) {
					String s = AbstractionUtils.modelCheck_ir(fileName);
					satisfied = AbstractionUtils.getMcmasResult(s);
				} else {
					String s = AbstractionUtils.modelCheck_IR(fileName);
					satisfied = AbstractionUtils.getMcmasResult(s);
				}
				if(satisfied) {
					if(formulaAux != formula1) {
						formulaAux.updateInnermostFormula("atom" + i, mapAtomToFormula);
						candidate.updateModel("atom" + i);
					}
					results.add(candidate.clone());
					i++;
				}
			} while(formulaAux != formula1 && satisfied);
		}
		return results;
	}

	public static Pair<Monitor, Monitor> createMonitor(String jsonModel, String subModelJson, String map) throws Exception {
		HashMap<String, String> mapAtomToFormula = new HashMap<>();
		for(String line : map.split("\n")) {
			mapAtomToFormula.put(line.split(":")[0], line.split(":")[1]);
		}
		AtlModel atlModel = JsonObject.load(jsonModel, AtlModel.class);
		AbstractionUtils.validateAtlModel(atlModel);
		AbstractionUtils.processDefaultTransitions(atlModel);
		AtlModel subModel = JsonObject.load(subModelJson, AtlModel.class);
		AbstractionUtils.validateAtlModel(subModel);
		AbstractionUtils.processDefaultTransitions(subModel);
		return createMonitor(atlModel, subModel, mapAtomToFormula);
	}

	public static Set<Pair<Monitor, Monitor>> createMonitors(AtlModel model, String subModelsFolder, boolean silent) throws Exception {
		// Set<Monitor> monitors = new HashSet<>();
		int i = 0;
		File folder = new File(subModelsFolder);
		if(!folder.exists() || !folder.isDirectory()) {
			throw new IllegalArgumentException("sub-models folder does not exist");
		}
		BufferedReader reader = new BufferedReader(new FileReader(folder.getAbsoluteFile() + "/map"));
		String line;
		HashMap<String, String> mapAtomToFormula = new HashMap<>();
		while((line = reader.readLine()) != null) {
			mapAtomToFormula.put(line.split(":")[0], line.split(":")[1]);
		}
//        for (final File fileEntry : folder.listFiles()) {
//            if(fileEntry.getName().equals("map")) {
//                continue;
//            }
//            if(!silent) System.out.println("Create monitor for sub-model " + i++);
//            String jsonModel = Files.readString(Paths.get(fileEntry.getAbsolutePath()), StandardCharsets.UTF_8);
//            // load json file to ATL Model Java representation
//            AtlModel subModel = JsonObject.load(jsonModel, AtlModel.class);
//            // validate the model
//            AbstractionUtils.validateAtlModel(subModel);
//            // add default transitions to the model
//            AbstractionUtils.processDefaultTransitions(subModel);
//            monitors.add(createMonitor(model, subModel));
//        }
//        return monitors;
		return Arrays.stream(folder.listFiles()).parallel().filter(fileEntry -> !fileEntry.getName().equals("map")).map(fileEntry -> {
			try {
				if(!silent) System.out.println("Creating monitor for " + fileEntry.getName().replace(".json", ""));
				String jsonModel = Files.readString(Paths.get(fileEntry.getAbsolutePath()), StandardCharsets.UTF_8);
				// load json file to ATL Model Java representation
				AtlModel subModel = JsonObject.load(jsonModel, AtlModel.class);
				// validate the model
				AbstractionUtils.validateAtlModel(subModel);
				// add default transitions to the model
				AbstractionUtils.processDefaultTransitions(subModel);
//                if(fileEntry.getName().contains("49")){
//                    String pippo = "";
//                }
				return createMonitor(model, subModel, mapAtomToFormula);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}).filter(Objects::nonNull).collect(Collectors.toSet());
	}

	public static Pair<Monitor, Monitor> createMonitor(AtlModel model, AtlModel subModel, HashMap<String, String> mapAtomToFormula) throws IOException {
		Optional<? extends State> initialState = subModel.getStates().stream().filter(State::isInitial).findFirst();
		if(initialState.isPresent()) {
			Optional<String> atom = initialState.get().getLabels().stream().filter(l -> l.startsWith("atom")).findFirst();
			if(atom.isPresent()) {
				String ltl = model.getFormula().extractLTL(subModel.getFormula(), initialState.get().getName(), mapAtomToFormula); //atom.get());
				// model.getState(initialState.get().getName()).getLabels().add(atom.get());
				List<String> alphabet = model.getStates().stream().map(State::getName).collect(Collectors.toList());
				//alphabet.addAll(model.getAgents().stream().map(Agent::getActions).findFirst().orElse(new ArrayList<>()));
				String[] aux = new String[alphabet.size()];
				alphabet.toArray(aux);
				return new ImmutablePair<>(
						new Monitor(ltl, subModel.getFormula().toStringWithStatesForAtoms(mapAtomToFormula), aux),
						new Monitor("F(" + initialState.get().getName() + ")", null, aux));
			}
		}
		return null;
	}

	public static String readSampleFile() {
		try {
			//File sampleFile = new ClassPathResource(MODEL_JSON_FILE_NAME).getFile();
			ClassPathResource cpr = new ClassPathResource(MODEL_JSON_FILE_NAME);

			byte[] bdata = FileCopyUtils.copyToByteArray(cpr.getInputStream());
			return new String(bdata, StandardCharsets.UTF_8);

			//return new String(FileUtils.readFileToByteArray(sampleFile));
		} catch (IOException ioe) {
			logger.error("Error while trying to read the sample file.", ioe);
		}

		return null;
	}
}
