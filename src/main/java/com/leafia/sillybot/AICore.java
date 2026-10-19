package com.leafia.sillybot;

import com.google.gson.*;
import com.leafia.sillybot.AITools.InvalidArgumentException;
import com.leafia.sillybot.AITools.WrongArgumentsException;
import com.sun.jna.Library;
import com.sun.jna.Native;
import de.kherud.llama.*;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
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
		FAST("TheDrummer_Snowpiercer-15B-v4-Q4_K_L",10,5,
				"<|im_start|>","<|im_start|>tool","<|im_start|>","<|im_start|>",
				"<|im_end|>","<|im_end|>tool","<|im_end|>","<|im_end|>"
		),
		FAST_GPU("TheDrummer_Snowpiercer-15B-v4-Q4_K_L",30,5,
				"<|im_start|>","<|im_start|>tool","<|im_start|>","<|im_start|>",
				"<|im_end|>","<|im_end|>tool","<|im_end|>","<|im_end|>"
		),
		MED_0("TheDrummer_Snowpiercer-15B-v4-Q8_0",18,4,
				"<|im_start|>","<|im_start|>tool","<|im_start|>","<|im_start|>",
				"<|im_end|>","<|im_end|>tool","<|im_end|>","<|im_end|>"
		),
		MED_1("internlm2-chat-20b_Q6_K",18,4,
				"<|im_start|>","<|im_start|>tool","<|im_start|>","<|im_start|>",
				"<|im_end|>","<|im_end|>tool","<|im_end|>","<|im_end|>"
		),
		SLOW("mrow",15,3,
				"<|system|>","<|tool|>","<|user|>","<|assistant|>",
				"","","",""
		),
		;
		public final String modelName;
		public final String start_user;
		public final String end_user;
		public final String start_ai;
		public final String end_ai;
		public final String start_sys;
		public final String end_sys;
		public final String start_tool;
		public final String end_tool;
		public final int layers;
		public final int maxTries;

		LLMType(String modelName,int layers,int maxTries,String start_sys,String start_tool,String start_user,String start_ai,String end_sys,String end_tool,String end_user,String end_ai) {
			this.modelName = modelName;
			this.start_user = start_user;
			this.end_user = end_user;
			this.start_ai = start_ai;
			this.end_ai = end_ai;
			this.start_sys = start_sys;
			this.end_sys = end_sys;
			this.start_tool = start_tool;
			this.end_tool = end_tool;
			this.layers = layers;
			this.maxTries = maxTries;
		}
	}
	public static LLMType curModel = LLMType.MED_1;
	private static boolean logging = false;
	public static final String JSON_SCHEMA = """
		{
		  "oneOf": [
		    {
		      "type": "array",
		      "minItems": 1,
		      "items": {
		        "type": "string"
		      }
		    },
		    {
		      "type": "array",
		      "minItems": 1,
		      "items": {
		        "type": "object",
		        "properties": {
		          "tool": { "type": "string" },
		          "args": {
		            "type": "array",
		            "items": { "type": "string" }
		          }
		        },
		        "required": ["tool", "args"],
		        "additionalProperties": false
		      }
		    }
		  ]
		}
		""";
	public static final String jsonGrammar = LlamaModel.jsonSchemaToGrammar(JSON_SCHEMA);
	public static final String TOOLLESS_SCHEMA = """
		{
		  "type": "array",
		  "minItems": 1,
		  "items": {
		    "type": "string"
		  }
		}
		""";
	public static final String riggedGrammar = LlamaModel.jsonSchemaToGrammar(TOOLLESS_SCHEMA);

	public static final String start_sys = "<[PROMPT SYS]>";
	public static final String end_sys = "<[/PROMPT SYS]>";
	public static final String start_user = "<[PROMPT USER]>";
	public static final String end_user = "<[/PROMPT USER]>";
	public static final String start_ai = "<[PROMPT AI]>";
	public static final String end_ai = "<[/PROMPT AI]>";
	public static final String start_tool = "<[PROMPT TOOL]>";
	public static final String end_tool = "<[/PROMPT TOOL]>";

	public static Pattern linkPat = Pattern.compile(".*(http?s://\\S+).*");
	public static final Gson gson = new Gson();
	public static String ask(MessageReceivedEvent evt,String guild,String userMessage,String userName) {
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
			userMessage = userMessage.replaceAll("<\\|\\D\\|>","");
		System.out.println("Prompt: "+userMessage);
		userList.add("["+userName+"]:");
		List<String> blockedList = new ArrayList<>(
				Arrays.asList("---","User:","[/INST]","[INST]")
		);
		blockedList.addAll(hardBlockedList);
		blockedList.addAll(userList);
		LlamaModelWrapper curLLM = llm;
		if (logging)
			System.out.println("Generating response...");

		boolean enableGrammar = true;

		String finalResponse = "too hard for me! >-<\n-# This is an error message!";
		StringBuilder base = new StringBuilder();
		int maxTries = curModel.maxTries;
		for (int tries = 0; tries < maxTries; tries++) {
			if (tries == maxTries-1)
				base.append("\n").append("You have reached maximum uses of tools. You cannot use tools any more.");
			StringBuilder promptText = getStringBuilder(guild,userMessage,userName,enableGrammar,base);
			InferenceParameters prompt =
					new InferenceParameters(promptText.toString()
							.replace(start_sys,curModel.start_sys).replace(end_sys,curModel.end_sys)
							.replace(start_user,curModel.start_user).replace(end_user,curModel.end_user)
							.replace(start_ai,curModel.start_ai).replace(end_ai,curModel.end_ai)
							.replace(start_tool,curModel.start_tool).replace(end_tool,curModel.end_tool)
					)
							.setTemperature(0.7f)
							.setStopStrings(toArray(blockedList))
							.setNPredict(128);
			if (enableGrammar)
				prompt.setGrammar(tries == maxTries-1 ? riggedGrammar : jsonGrammar);

			StringBuilder response = new StringBuilder();
			if (!enableGrammar)
				response.append("[");
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
			if (!curLLM.isOnline())
				return "ERROR! o-o\nReason: LLM was requested to switch and had to be terminated!";
			base.append(response).append(end_ai);
			{
				try {
					JsonArray array = gson.fromJson(response.toString(),JsonArray.class);
					int mode = -1;
					for (JsonElement element : array) {
						if (element instanceof JsonPrimitive) {
							if (mode != -1 && mode != 0) {
								mode = -2;
								break;
							} else mode = 0;
						} else if (element instanceof JsonObject) {
							if (mode != -1 && mode != 1) {
								mode = -2;
								break;
							} else mode = 1;
						}
					}
					if (mode == -2) {
						base.append(start_tool).append("\nERROR: You cannot have both json string and json object in your output. Make it only string or objects.").append(end_tool);
						continue;
					}
					if (mode == 0) {
						StringBuilder builder = new StringBuilder();
						for (JsonElement element : array) {
							if (element instanceof JsonPrimitive prim) {
								if (!builder.isEmpty())
									builder.append("\n");
								builder.append(prim.getAsString());
							}
						}
						finalResponse = builder.toString();
						break;
					} else {
						StringBuilder results = new StringBuilder("Results for the preceding assistant tool-call message:");
						StringBuilder faileds = new StringBuilder("Errors:");
						for (JsonElement element : array) {
							if (element instanceof JsonObject obj) {
								String tostr = element.toString();
								if (!obj.has("tool") && !obj.has("args")) {
									faileds.append("\n").append(tostr).append(": ERROR: Couldn't find \"tool\" and \"args\" values!");
									continue;
								}
								if (!obj.has("tool")) {
									faileds.append("\n").append(tostr).append(": ERROR: Couldn't find \"tool\" value!");
									continue;
								}
								if (!obj.has("args")) {
									faileds.append("\n").append(tostr).append(": ERROR: Couldn't find \"args\" value!");
									continue;
								}
								String func = obj.get("tool").getAsString();
								JsonArray args = obj.get("args").getAsJsonArray();
								if (AITools.tools.containsKey(func)) {
									List<String> list = new ArrayList<>();
									for (JsonElement arg : args) {
										if (arg instanceof JsonPrimitive prim)
											list.add(prim.getAsString());
										else
											list.add(arg.toString());
									}
									try {
										String result = AITools.tools.get(func).func.call(evt,list);
										results.append("\n").append(tostr).append(": results: ").append(result);
										//toolResponses.append("\n<QUICK DISCLAIMER: The tool ").append(func).append(" has been called successfully. Answer the user with the result above. Do NOT call again for the same purpose.>");
										System.out.println("Used tool "+func+" with args "+args+", results: "+result);
									} catch (WrongArgumentsException exc) {
										faileds.append("\n").append(tostr).append(": ERROR: Expected ").append(exc.required).append(" arguments, got ").append(exc.provided).append("!");
									} catch (Exception e) {
										faileds.append("\n").append(tostr).append(": ERROR: ").append(e.getMessage());
									}
								}
							} else {
								faileds.append("\nERROR: Detected an element in your JSON output that is neither a string (answer) or a json object (tool call)!");
							}
						}
						results.append("\n").append(faileds);
						results.append("\n\nRead every result above. You may call the same tool again with DIFFERENT arguments. Do not repeat an identical completed call. If you now have enough information, return [\"(your final answer)\"].");
						base.append("\n").append(start_tool).append(results).append(end_tool);
					}
				} catch (JsonSyntaxException ignored) {
					base.append("\nERROR: Malformed JSON, couldn't process your output!");
				}
			}
		}
		if (logging)
			System.out.println("\nGeneration finished.");
		/*
		String res = response.toString();
		res = res.replace("\n\n","\n");
		if (res.startsWith("\\. "))
			res = res.substring(2);
		if (res.startsWith(","))
			res = res.substring(1);*/
		String res = finalResponse;
		while (true) {
			Matcher matcher = linkPat.matcher(res);
			if (matcher.matches()) {
				String link = matcher.group(1);
				res = res.replace(link,"[Hyperlink Blocked]");
				System.out.println("Blocked link "+link);
			} else
				break;
		}
		//res = res.trim();
		if (logging)
			System.out.println(res);
		List<String> history = getHistory(guild);
		history.add(start_user+"User: ["+userName+"]:\n" + userMessage + "\n"+end_user);
		history.add(start_ai+"Mrowia:\n"+res + "\n"+end_ai);
		while (history.size() > memory*2)
			history.removeFirst();
		return res;
	}
	public static int memory = 200;

	public static void logMessage(String guild,String userMessage,String userName) {
		List<String> history = getHistory(guild);
		history.add(start_user+"User: ["+userName+"]:\n" + userMessage + "\n"+end_user);
	}

	private static StringBuilder getStringBuilder(String guild,String userMessage,String userName,boolean enableGrammar,StringBuilder base) {

		StringBuilder promptText = new StringBuilder();
		{
			promptText.append(start_sys);
			promptText.append("Follow this character background and personality. ");
			promptText.append("It is background information, not a question to answer.\n\n");
			promptText.append("### Character information\n");
			promptText.append(context);
			promptText.append(end_sys+"\n");
			{
				promptText.append(start_sys+"""
									You must output exactly one valid JSON array, with no text before or after it.
									For a final answer, output strings in an array, like ["your response"].
									If you respond multiple strings in an array like ["A","B","C"], it will be concentrated with a line break.
									To call a tool, output an array containing one or more objects, each shaped like {"tool":"EXACT_TOOL_NAME","args":["argument 1","argument 2"]}.
									Example tool call: [{"tool":"reverseOrder","args":["Hello","World"]}].
									You may call the same tool multiple times with different arguments.
									After a tool call, its result will be provided in a following tool message. Read all results before deciding what to do next.
									Do not repeat an identical completed call; use its recorded result. Do not answer until you have enough information.
									Only use tools when you think they're necessary. Do not needlessly call them.
									Never put a final-answer string and tool-call objects in the same array.""");
				promptText.append("\n");
				promptText.append("\nAvailable tools for you:");
				AITools.appendToolList(promptText);
				promptText.append("\n"+end_sys+"\n");
			}

			// Include previous conversation turns.
			List<String> history = getHistory(guild);
			for (String turn : history) {
				promptText.append(turn);
			}

			promptText.append(start_sys+"\n");
			promptText.append("These messages above are previous messages sent in this chat.\n");
			promptText.append("Only read them when necessary.\n");
			promptText.append("**The prompt below is the real question being asked by a user.**\n");
			promptText.append(end_sys+"\n");

			// Add the current user message.
			promptText.append(start_user+"User: ["+userName+"]:\n");
			promptText.append(userMessage);
			promptText.append(end_user);
			base.append("\n"+start_ai+"Mrowia (in JSON format):\n");
			promptText.append(base);
			if (!enableGrammar)
				promptText.append("[");
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
				/*System.load("D:\\cuda\\bin\\x64\\cudart64_13.dll");
				System.out.println("cudart loaded");

				System.load("D:\\cuda\\bin\\x64\\nvJitLink_130_0.dll");
				System.out.println("nvJitLink loaded");*/

				System.load("D:\\cuda\\bin\\x64\\cublasLt64_13.dll");
				System.out.println("cuBLASLt loaded");

				System.load("D:\\cuda\\bin\\x64\\cublas64_13.dll");
				System.out.println("cuBLAS loaded");
			} catch (UnsatisfiedLinkError e) {
				e.printStackTrace();
			}
			// kms
			System.load(
					"D:\\test_llama\\java-llama.cpp\\src\\main\\resources_linux_cuda\\de\\kherud\\llama\\Windows\\x86_64\\jllama.dll"
			);

			loadModel();
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
			ask(null,"0",reader.readLine(),"User");
		}
	}
}
