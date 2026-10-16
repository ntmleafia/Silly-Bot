package com.leafia.sillybot;

import com.sun.jna.Library;
import com.sun.jna.Native;
import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.LlamaOutput;
import de.kherud.llama.ModelParameters;

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
	public static final String context;
	public static final Set<String> userList = new HashSet<>();
	public static String[] toArray(List<String> list) {
		String[] a = new String[list.size()];
		for (int i = 0; i < list.size(); i++)
			a[i] = list.get(i);
		return a;
	}
	public static final List<String> hardBlockedList = new ArrayList<>();
	static {
		try {
			context = Files.readString(fursona.toPath().toAbsolutePath());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
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
	public static LlamaModel llm;
	public static List<String> history = new ArrayList<>();
	public static final String start = "<|im_start|>";
	public static final String end = "<|im_end|>";
	private static boolean logging = false;
	public static String ask(String userMessage,String userName) {
		if (llm == null) {
			return "I'm not ready yet!";
		}
		userMessage = userMessage.replace("<s>","");
		userMessage = userMessage.replace("</s>","");
		userMessage = userMessage.replace("[INST]","");
		userMessage = userMessage.replace("[/INST]","");
		userMessage = userMessage.replace(start,"");
		userMessage = userMessage.replace(end,"");
		userList.add("User ["+userName+"]:");
		StringBuilder promptText = getStringBuilder(userMessage,userName);
		List<String> blockedList = new ArrayList<>(
				Arrays.asList("---","User:")
		);
		blockedList.addAll(hardBlockedList);
		blockedList.addAll(userList);
		InferenceParameters prompt =
				new InferenceParameters(promptText.toString())
						.setTemperature(0.7f)
						.setStopStrings(toArray(blockedList))
						.setNPredict(128);
		if (logging)
			System.out.println("Generating response...");
		StringBuilder response = new StringBuilder();
		for (LlamaOutput output : llm.generate(prompt)) {
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
		String res = response.toString();
		res = res.replace("\n\n","\n");
		if (res.startsWith("\\. "))
			res = res.substring(2);
		if (res.startsWith(","))
			res = res.substring(1);
		res = res.trim();
		if (logging)
			System.out.println(res);
		history.add(start+"User: ["+userName+"]:\n" + userMessage + "\n"+end);
		history.add(start+"Mrowia:\n"+res + "\n"+end);
		while (history.size() > 20*2)
			history.removeFirst();
		return res;
	}

	public static void logMessage(String userMessage,String userName) {
		history.add(start+"User: ["+userName+"]:\n" + userMessage + "\n"+end);
	}

	private static StringBuilder getStringBuilder(String userMessage,String userName) {
		StringBuilder promptText = new StringBuilder();
		{
			promptText.append("[INST] "+start);
			promptText.append("Follow this character background and personality. ");
			promptText.append("It is background information, not a question to answer.\n\n");
			promptText.append("### Character information\n");
			promptText.append(context);
			promptText.append(end+"\n\n");

			// Include previous conversation turns
			for (String turn : history)
				promptText.append(turn);

			// Add the current user message
			promptText.append(start+"User: ["+userName+"]:\n");
			promptText.append(userMessage);
			promptText.append(end+"\n");
			promptText.append(start+"Mrowia:\n");
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
			} catch (UnsatisfiedLinkError e) {
				e.printStackTrace();
			}

			ModelParameters mrow = new ModelParameters()
					.setModel("../TheDrummer_Snowpiercer-15B-v4-Q4_K_L.gguf") // not gemma-4-12b-it-qat-q4_0 anymore
					.setGpuLayers(10);

			//if (true) return;
			try (LlamaModel model = new LlamaModel(mrow)) {
				llm = model;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	// debug
	static void main(String[] args) throws IOException {
		logging = true;
		init();
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		while (true) {
			System.out.println("Ask:");
			ask(reader.readLine(),"User");
		}
	}
}
