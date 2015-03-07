package com.mediatek.nfc.configutil;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.Map;
import java.util.HashMap;
import android.util.Log;

public class ConfigUtil {
    public interface IParser {
        public boolean parse(String path);
		public boolean get(int keyIdIn, int[] valueIdOut);
    }

    public interface IRules {
        public boolean search(String key, String value, Map<Integer, Integer> keyValueMap);
    }    
    
    static public IParser createParser(String rules[]) {
        return new ConfigFileParser(new ConfigRules(rules));
    }
	
	static class ConfigRules implements IRules {		
		private Map<String, KeyValue> mKeyValueMap = new HashMap<String, KeyValue>();

		static class KeyValue {
			private int mKeyId;
			private Map<String, Integer> mValueMap = new HashMap<String, Integer>();
			
			String init(String rule) {
				/// rule string is KEY=0:VALUE1=1,VALUE2=2,VALUE3=3
				int keyId = -1;
				String tokens[] = rule.split(":");
				String key = tokens[0].split("=")[0];
				mKeyId = Integer.parseInt(tokens[0].split("=")[1]);
				tokens = tokens[1].split(",");
				for (String token : tokens) {
					String t[] = token.split("=");
					mValueMap.put(t[0], Integer.parseInt(t[1]));
				}
				return key;
			}
			
			int get(String value) {
				return mValueMap.get(value);
			}
			
			int id() {
				return mKeyId;
			}
		}
		
		public ConfigRules(String rules[]) {
			for (String rule : rules) {
				KeyValue keyValue = new KeyValue();
				String key = keyValue.init(rule);
				mKeyValueMap.put(key, keyValue); 
			}
		}
		
		public boolean search(String key, String value, Map<Integer, Integer> keyValueMap) {
			try {
				KeyValue keyValue = mKeyValueMap.get(key);
				keyValueMap.put(keyValue.id(), keyValue.get(value));
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
			return true;
		}
	}    
	
	static class ConfigFileParser implements IParser {
		static private final String TAG = "ConfigFileParser";
		
		private IRules mRules;
		private Map<Integer, Integer> mKeyValueMap = new HashMap<Integer, Integer>();
		public ConfigFileParser(IRules rules) {
			mRules = rules;
		}
		
		public boolean parse(String path) {
			BufferedReader bufferedReader = null;
			try {
				bufferedReader = new BufferedReader(new FileReader(path));
			} catch (Exception e) {
				debugPrint("cannot open file, path = " + path);
				e.printStackTrace();
				return false;
			}
			
			while (true) {
				try {
					/// read a line
					String line = bufferedReader.readLine();
					debugPrint("line: " + line);
					
					/// if the line is null, terminate the parsing
					if (line == null) {
						break;
					}
					
					/// if this line starts with a #, treat it as comment
					line = line.trim();
					if (line.length() > 0 && line.charAt(0) == '#') {
						debugPrint("    --> it's a comment");
						continue;
					} 
					
					/// handle the "key : value" pair
					String tokens[] = line.split(":");
					if (tokens.length == 2) {
						String key = tokens[0];
						String value = tokens[1];
						if (!mRules.search(key, value, mKeyValueMap)) {
							debugPrint("invalid rule line");
						}
					}
				} catch (Exception e) {
					debugPrint("exception during parsing");
					e.printStackTrace();
					break;
				}
			}
			///TODO: close the File object
			if (bufferedReader != null) {
				try {
					bufferedReader.close();
				} catch (Exception e) {}
			}
			return true;
		}
		
		public boolean get(int keyId, int[] valueIdOut) {
			try {
				int out = mKeyValueMap.get(keyId);
				valueIdOut[0] = out; 
			} catch (Exception e) {
				return false;
			}
			return true;
		}
		
		private void dump() {
			for (Integer key : mKeyValueMap.keySet()) {
				debugPrint("key: " + key + ", value = " + mKeyValueMap.get(key));
			}
		}
		
		private void debugPrint(String msg) {
			Log.d(TAG, msg);
		}
		
	}
    
}
