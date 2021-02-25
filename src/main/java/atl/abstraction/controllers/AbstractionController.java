package atl.abstraction.controllers;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import atl.abstraction.beans.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import atl.abstraction.AbstractionUtils;

@Controller
public class AbstractionController {

	private static final String INDEX_PAGE = "index";
    private final Log logger = LogFactory.getLog(getClass());

	
	@GetMapping("/")
	public String transformForm(Model model) throws Exception {
		TransformBean transformBean = new TransformBean();
		transformBean.setAtlModel(AbstractionUtils.readSampleFile());
		/*AtlModel atlModel = JsonObject.load(transformBean.getAtlModel(), AtlModel.class);
		AbstractionUtils.validateAtlModel(atlModel);
		AbstractionUtils.processDefaultTransitions(atlModel);
		transformBean.setDotAtlModel(AbstractionUtils.generateDotGraph(atlModel));*/
		model.addAttribute("transformBean", transformBean);
		return INDEX_PAGE;
	}

	@PostMapping("/extract")
	public String transformSubmit(@ModelAttribute TransformBean transformBean, Model model) throws Exception {
		try {
			if (StringUtils.isBlank(transformBean.getAtlModel())) {
				throw new Exception("Please provide an input json model to transform!");
			}
			AtlModel atlModel = JsonObject.load(transformBean.getAtlModel(), AtlModel.class);

			AbstractionUtils.validateAtlModel(atlModel);
			AbstractionUtils.processDefaultTransitions(atlModel);
	    	transformBean.setDotAtlModel(AbstractionUtils.generateDotGraph(atlModel));

			File file = new File("./tmp");
			if(!file.exists() && !file.mkdir()) {
				throw new FileSystemException("./tmp folder could not be created");
			}
			file = new File("./tmp/ir");
			if(!file.exists() && !file.mkdir()) {
				throw new FileSystemException("./tmp/ir folder could not be created");
			}
			file = new File("./tmp/IR");
			if(!file.exists() && !file.mkdir()) {
				throw new FileSystemException("./tmp/IR folder could not be created");
			}
			AbstractionUtils.mcmas = System.getenv("mcmas_home");
			Monitor.rv = System.getenv("lamaconv_home");

			HashMap<String, String> mapAtomToFormula = new HashMap<>();

			List<SubModel> subModels = new ArrayList<>();
			int i = 0;
			if(transformBean.getImperfectRecall()) {
				List<AtlModel> subModelsir = allSubICGSWithImperfectRecall(atlModel, true, mapAtomToFormula);
				for (AtlModel m : subModelsir) {
					subModels.add(
							new SubModel(
									"M" + (i++),
									m.toString(),
									m.getFormula().toString(),
									AbstractionUtils.generateDotGraph(m),
									"",
									""));

				}
			}
			if(transformBean.getPerfectInfo()){
				List<AtlModel> subModelsIR = allSubICGSWithPerfectInformation(atlModel, true, mapAtomToFormula);
				for (AtlModel m : subModelsIR) {
					subModels.add(
							new SubModel(
									"M" + (i++),
									m.toString(),
									m.getFormula().toString(),
									AbstractionUtils.generateDotGraph(m),
									"",
									""));
				}
			}
			model.addAttribute("subModels", subModels);
			StringBuilder map = new StringBuilder();
			for(Map.Entry<String, String> item : mapAtomToFormula.entrySet()) {
				map.append(item.getKey()).append(":").append(item.getValue()).append("\n");
			}
			transformBean.setMap(map.toString());
/*
			FileUtils.cleanDirectory(new File("./tmp/ir"));
			int i = 0;
			for(AtlModel m : subModelsir) {
				FileWriter writer = new FileWriter("./tmp/ir/" + "/subModel" + i++ + ".json");
				writer.append(m.toString()).append("\n\n");
				writer.close();
			}
			FileWriter writer = new FileWriter("./tmp/ir" + "/map");
			for(Map.Entry<String, String> item : Formula.getMapAtomToFormula().entrySet()) {
				writer.append(item.getKey()).append(":").append(item.getValue()).append("\n");
			}
			writer.close();*/
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			String error = StringUtils.isNotBlank(e.getMessage())?e.getMessage():ExceptionUtils.getStackTrace(e);
			model.addAttribute("error", error);
		}
		return INDEX_PAGE;
	}

	@PostMapping("/rv")
	@ResponseBody
	public String rvSubmit(@ModelAttribute TransformBean transformBean, Model model) throws Exception {
		String map = transformBean.getMap();
		String subModel = transformBean.getSubModel();
		String atlModel = transformBean.getAtlModel();
		String trace = transformBean.getTrace();

		Pair<Monitor, Monitor> monitor = AbstractionUtils.createMonitor(atlModel, subModel, map);
		Boolean res = execRV(monitor, Arrays.asList(trace.split(",")));
		return "Result: " + (res == null ? "?" : (res ? "True" : "False"));
	}


	public static List<AtlModel> allSubICGSWithImperfectRecall(AtlModel model, boolean silent, HashMap<String, String> mapAtomToFormula) throws Exception {
		System.out.println("Generating sub-models..");
		List<AtlModel> candidates = AbstractionUtils.allModels(model);
		System.out.println("Sub-models generated: " + candidates.size());
		return AbstractionUtils.validateSubModels(model, candidates, true, silent, mapAtomToFormula);
	}

	public static List<AtlModel> allSubICGSWithPerfectInformation(AtlModel model, boolean silent, HashMap<String, String> mapAtomToFormula) throws Exception {
		System.out.println("Generating sub-models..");
		List<AtlModel> candidates = new LinkedList<>();
		candidates.add(model);
		List<AtlModel> candidatesPP = new LinkedList<>();
		while(!candidates.isEmpty()) {
			AtlModel candidate = candidates.remove(0);
			boolean valid = true;
			for(Agent agent : candidate.getAgents()){
				if(!agent.getIndistinguishableStates().isEmpty()) {
					for(List<String> indistinguishableStates : agent.getIndistinguishableStates()) {
						for (String ind : indistinguishableStates) {
							AtlModel aux = candidate.clone();
							State s = new State();
							s.setName(ind);
							aux.removeState(s);
							candidates.add(aux);
						}
					}
					valid = false;
					break;
				}
			}
			if(valid) {
				if(candidatesPP.stream().noneMatch((m) -> new HashSet<>(m.getStates()).equals(new HashSet<>(candidate.getStates())))) {
					candidatesPP.add(candidate);
				}
			}
		}
		System.out.println("Sub-models generated: " + candidatesPP.size());
		return AbstractionUtils.validateSubModels(model, candidatesPP, false, silent, mapAtomToFormula);
	}

	public static Boolean execRV(Pair<Monitor, Monitor> monitor, Collection<String> trace) throws IOException {
		for(String event : trace) {
			System.out.println("Analyse event: " + event);
			event = event.trim();
			Monitor.Verdict output;
			if(monitor.getLeft().getCurrentVerdict() == Monitor.Verdict.True) {
				output = monitor.getRight().next(event);
				if(output == Monitor.Verdict.True) {
					return true;
				}
			} else {
				output = monitor.getLeft().next(event);
				if(output == Monitor.Verdict.False) {
					return false;
				}
			}
		}
		return null;
	}

	public static void execRV(Set<Pair<Monitor, Monitor>> monitors, Collection<String> trace) throws IOException {
		for(String event : trace) {
			System.out.println("Analyse event: " + event);
			Set<Pair<Monitor,Monitor>> monitorsAux = new HashSet<>();
			Set<Pair<String, String>> satisfiedFormulas = new HashSet<>();
			for(Pair<Monitor,Monitor> monitor : monitors) {
				Monitor.Verdict output;
				if(monitor.getLeft().getCurrentVerdict() == Monitor.Verdict.True) {
					output = monitor.getRight().next(event);
					if(output == Monitor.Verdict.True) {
						satisfiedFormulas.add(new ImmutablePair<>(monitor.getLeft().getLtl(), monitor.getLeft().getAtl()));
					} else if(output == Monitor.Verdict.Unknown) {
						monitorsAux.add(monitor);
					}
				} else {
					output = monitor.getLeft().next(event);
					if(output != Monitor.Verdict.False) {
						monitorsAux.add(monitor);
					}
				}
			}
			monitors = monitorsAux;
			for(Pair<String, String> p : satisfiedFormulas) {
				System.out.println("- Monitor concluded satisfaction of LTL property: " + p.getLeft());
				System.out.println("and reached a sub-model where the ATL property: " + p.getRight() + " is satisfied.");
			}
			if(!satisfiedFormulas.isEmpty()) {
				System.out.println("Do you want to continue monitoring the system? [y/n]");
				Scanner scanner = new Scanner(System.in);
				String choice = scanner.next();
				if(choice.equals("n")) {
					return;
				}
			}
		}
	}
	
}