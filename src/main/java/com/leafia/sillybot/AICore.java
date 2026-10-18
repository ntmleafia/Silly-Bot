package com.leafia.sillybot;

import com.sun.jna.Library;
import com.sun.jna.Native;
import de.kherud.llama.*;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AICore {
	public interface Kernel32 extends Library {
		Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);
		boolean SetDllDirectoryW(String path);
	}
	public static final File fursona = new File("../fursona1.txt");
	public static String context;
	public static final Set<String> userList = new HashSet<>();
	public static String[] toArray(List<String> list) {
		String[] a = new String[list.size()];
		for (int i = 0; i < list.size(); i++)
			a[i] = list.get(i);
		return a;
	}
	public static final List<String> hardBlockedList = new ArrayList<>();
	public static void reloadFursona() {
		try {
			context = Files.readString(fursona.toPath().toAbsolutePath());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	static {
		reloadFursona();
		hardBlockedList.addAll(Arrays.asList(
				"You are Mrowia","Mrowia:"
		));
	}
	public static int checkForBlocked(String s) {
		for (String b : hardBlockedList) {
			if (s.contains(b))
				return s.indexOf(b);
		}
		for (String b : userList) {
			if (s.contains(b))
				return s.indexOf(b);
		}
		return -1;
	}
	public static Pattern userPat = Pattern.compile(".*(User: \\[.*\\]:).*");
	public static int checkForUserRegex(String s) {
		Matcher matcher = userPat.matcher(s);
		if (matcher.matches()) {
			String match = matcher.group(1);
			return s.indexOf(match);
		}
		return -1;
	}
	public static LlamaModelWrapper llm;
	public static class LlamaModelWrapper {
		LlamaModel model;
		boolean online = true;
		public LlamaModelWrapper(ModelParameters model) {
			this.model = new LlamaModel(model);
		}
		public boolean isOnline() {
			return online;
		}
		public void close() {
			model.close();
			online = false;
		}
		public static class LlamaIterWrapper implements Iterator<LlamaOutput> {
			final LlamaModelWrapper wrapper;
			final LlamaIterator iter;
			public LlamaIterWrapper(LlamaModelWrapper wrapper,LlamaIterator iter) {
				this.wrapper = wrapper;
				this.iter = iter;
			}
			@Override
			public boolean hasNext() {
				return wrapper.online && iter.hasNext();
			}
			@Override
			public LlamaOutput next() {
				if (!wrapper.online)
					throw new RuntimeException("next() called after termination");
				return iter.next();
			}
		}
		@FunctionalInterface
		public interface LlamaWrapperIterable extends Iterable<LlamaOutput> {
			@NotNull
			@Override
			LlamaIterWrapper iterator();
		}
		public LlamaWrapperIterable generate(InferenceParameters parameters) {
			if (!online)
				return ()->new LlamaIterWrapper(this,null);
			return ()->new LlamaIterWrapper(this,this.model.generate(parameters).iterator());
		}
	}
	public static Map<String,List<String>> history = new HashMap<>();
	public static List<String> getHistory(String channelId) {
		if (!history.containsKey(channelId))
			history.put(channelId,new ArrayList<>());
		return history.get(channelId);
	}
	public enum LLMType {
		FAST("TheDrummer_Snowpiercer-15B-v4-Q4_K_L",10,
				"<|im_start|>","<|im_start|>","<|im_start|>",
				"<|im_end|>","<|im_end|>","<|im_end|>"
		),
		SLOW("mrow",15,
				"<|system|>","<|user|>","<|assistant|>",
				"","",""
		),
		;
		public final String modelName;
		public final String start_user;
		public final String end_user;
		public final String start_ai;
		public final String end_ai;
		public final String start_sys;
		public final String end_sys;
		public final int layers;

		LLMType(String modelName,int layers,String start_sys,String start_user,String start_ai,String end_sys,String end_user,String end_ai) {
			this.modelName = modelName;
			this.start_user = start_user;
			this.end_user = end_user;
			this.start_ai = start_ai;
			this.end_ai = end_ai;
			this.start_sys = start_sys;
			this.end_sys = end_sys;
			this.layers = layers;
		}
	}
	public static LLMType curModel = LLMType.FAST;
	private static boolean logging = false;

	public static final String start_sys = "<[PROMPT SYS]>";
	public static final String end_sys = "<[/PROMPT SYS]>";
	public static final String start_user = "<[PROMPT USER]>";
	public static final String end_user = "<[/PROMPT USER]>";
	public static final String start_ai = "<[PROMPT AI]>";
	public static final String end_ai = "<[/PROMPT AI]>";

	public static Pattern linkPat = Pattern.compile(".*(http?s://\\S+).*");
	public static String ask(String guild,String userMessage,String userName) {
		if (llm == null)
			return "I'm not ready yet!";
		userMessage = userMessage.replace("<s>","");
		userMessage = userMessage.replace("</s>","");
		userMessage = userMessage.replace("[INST]","");
		userMessage = userMessage.replace("[/INST]","");
		userMessage = userMessage.replace(start_sys,"");
		userMessage = userMessage.replace(end_sys,"");
		userMessage = userMessage.replace(start_user,"");
		userMessage = userMessage.replace(end_user,"");
		userMessage = userMessage.replace(start_ai,"");
		userMessage = userMessage.replace(end_ai,"");
		for (LLMType value : LLMType.values())
			userMessage = userMessage.replaceAll("<\\|.*\\|>","");
		userList.add("["+userName+"]:");
		StringBuilder promptText = getStringBuilder(guild,userMessage,userName);
		List<String> blockedList = new ArrayList<>(
				Arrays.asList("---","User:","[/INST]")
		);
		blockedList.addAll(hardBlockedList);
		blockedList.addAll(userList);
		LlamaModelWrapper curLLM = llm;
		InferenceParameters prompt =
				new InferenceParameters(promptText.toString()
						.replace(start_sys,curModel.start_sys).replace(end_sys,curModel.end_sys)
						.replace(start_user,curModel.start_user).replace(end_user,curModel.end_user)
						.replace(start_ai,curModel.start_ai).replace(end_ai,curModel.end_ai)
				)
						.setTemperature(0.7f)
						.setStopStrings(toArray(blockedList))
						.setNPredict(128);
		if (logging)
			System.out.println("Generating response...");
		StringBuilder response = new StringBuilder();
		for (LlamaOutput output : curLLM.generate(prompt)) {
			SillyBot.sendTyping();
			String token = output.toString();
			response.append(token);
			if (response.toString().length() > 3000) {
				response = new StringBuilder("ERROR! o-o\nReason: Exceeded max length!");
				break;
			}
			int i = checkForBlocked(response.toString());
			int i2 = checkForUserRegex(response.toString());
			if (logging) {
				System.out.print(token);
				System.out.flush();
			}
			if (i != -1) {
				response.delete(i,response.length());
				System.out.println("WARNING: stop strings did not work: had to terminate.");
				break;
			}
			if (i2 != -1) {
				response.delete(i2,response.length());
				System.out.println("Terminated due to user regex detection.");
				break;
			}
		}
		if (logging)
			System.out.println("\nGeneration finished.");
		if (!curLLM.isOnline())
			return "ERROR! o-o\nReason: LLM was requested to switch and had to be terminated!";
		String res = response.toString();
		res = res.replace("\n\n","\n");
		if (res.startsWith("\\. "))
			res = res.substring(2);
		if (res.startsWith(","))
			res = res.substring(1);
		while (true) {
			Matcher matcher = linkPat.matcher(res);
			if (matcher.matches()) {
				String link = matcher.group(1);
				res = res.replace(link,"[Hyperlink Blocked]");
				System.out.println("Blocked link "+link);
			} else
				break;
		}
		res = res.trim();
		if (logging)
			System.out.println(res);
		List<String> history = getHistory(guild);
		history.add(start_user+"User: ["+userName+"]:\n" + userMessage + "\n"+end_user);
		history.add(start_ai+"Mrowia:\n"+res + "\n"+end_ai);
		while (history.size() > memory*2)
			history.removeFirst();
		return res;
	}
	public static int memory = 20;

	public static void logMessage(String guild,String userMessage,String userName) {
		List<String> history = getHistory(guild);
		history.add(start_user+"User: ["+userName+"]:\n" + userMessage + "\n"+end_user);
	}

	private static StringBuilder getStringBuilder(String guild,String userMessage,String userName) {
		StringBuilder promptText = new StringBuilder();
		{
			promptText.append("[INST] "+start_sys);
			promptText.append("Follow this character background and personality. ");
			promptText.append("It is background information, not a question to answer.\n\n");
			promptText.append("### Character information\n");
			promptText.append(context);
			promptText.append(end_sys+"\n\n");

			List<String> history = getHistory(guild);
			// Include previous conversation turns
			for (String turn : history)
				promptText.append(turn);

			// Add the current user message
			promptText.append(start_user+"User: ["+userName+"]:\n");
			promptText.append(userMessage);
			promptText.append(end_user+"\n");
			promptText.append(start_ai+"Mrowia:\n");
			promptText.append("[/INST]");
		}
		return promptText;
	}

	public static void init() {
		try {

			// fuck off
			System.setProperty(
					"de.kherud.llama.lib.path",
					"D:\\test_llama\\java-llama.cpp\\src\\main\\resources_linux_cuda\\de\\kherud\\llama\\Windows\\x86_64\\"
			);
			// doesnt detect my gpu without ts
			try {
				System.load("D:\\cuda\\bin\\x64\\cublasLt64_13.dll");
				System.out.println("cuBLASLt loaded");

				System.load("D:\\cuda\\bin\\x64\\cublas64_13.dll");
				System.out.println("cuBLAS loaded");

				loadModel();
			} catch (UnsatisfiedLinkError e) {
				e.printStackTrace();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void loadModel() {
		ModelParameters mrow = new ModelParameters()
				.setModel("../"+curModel.modelName+".gguf") // not gemma-4-12b-it-qat-q4_0 anymore
				.setGpuLayers(curModel.layers);

		//if (true) return;
		llm = new LlamaModelWrapper(mrow);
	}

	public static void switchModel(LLMType type) {
		if (llm == null)
			return;
		llm.close();
		llm = null;
		curModel = type;
		loadModel();
	}

	// debug
	static void main(String[] args) throws IOException {
		logging = true;
		init();
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		while (true) {
			System.out.println("Ask:");
			ask("0",reader.readLine(),"User");
		}
	}
}
