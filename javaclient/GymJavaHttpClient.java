package javaclient;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.Set;

import org.json.*;

/**
 * Contains methods that correspond with the OpenAI Gym HTTP API 
 * (https://github.com/openai/gym-http-api), check there for more details about the methods.
 * @author Ryan Amaral - (ryan-amaral on GitHub)
 */
public class GymJavaHttpClient {

    public static String baseUrl = "http://127.0.0.1:5000"; // probably "http://127.0.0.1:5000"
    private static HttpURLConnection con; // object to use to create and do stuff with connection
    private HttpURLConnection con1; // connection for object level

    /**
     * List all of the environments you started that are currently running on the server.
     * @return A set of the environments' instance Id's.
     */
    public static Set<String> listEnvs() {
        connect("/v1/envs/", "GET", null);
        return getJson().getJSONObject("all_envs").keySet();
    }
    public Set<String> listEnvs1() {
        connect1("/v1/envs/", "GET", null);
        return getJson1().getJSONObject("all_envs").keySet();
    }

    /**
     * Creates a new environment of the type specified.
     * @param envId The id of the environment to create (ex: "CartPole-v0").
     * @return The instance id of the created environment.
     */
    public static String createEnv(String envId) {

        connect("/v1/envs/", "POST", "{\"env_id\":\"" + envId + "\"}");
        return getJson().getString("instance_id");
    }
    public String createEnv1(String envId) {
        connect1("/v1/envs/", "POST", "{\"env_id\":\"" + envId + "\"}");
        return getJson1().getString("instance_id");
    }

    /**
     * Resets the selected environment.
     * @param instanceId The id of the environment.
     * @return Whatever the observation of the environment is. Probably JSONArray.
     */
    public static Object resetEnv(String instanceId) {
        connect("/v1/envs/" + instanceId + "/reset/", "POST", "{\"instance_id\":\"" + instanceId + "\"}");
        return getJson().get("observation"); // probably of type JSONArray
    }
    public Object resetEnv1(String instanceId) {
        connect1("/v1/envs/" + instanceId + "/reset/", "POST", "{\"instance_id\":\"" + instanceId + "\"}");
        return getJson1().get("observation"); // probably of type JSONArray
    }

    /**
     * Steps the environment.
     * @param instanceId The id of the environment.
     * @param action The action to do in the step.
     * @param isDiscreteSpace Whether space in the environment is discrete or not.
     * @return A StepObject, check out that class.
     */
    public static StepObject stepEnv(String instanceId, double action, boolean isDiscreteSpace, boolean render) {
        if (isDiscreteSpace) {
        	connect("/v1/envs/" + instanceId + "/step/", "POST",
                    "{\"instance_id\":\"" + instanceId + "\", \"action\":" + (int) action + 
                    ", \"render\":" + Boolean.toString(render) + "}");
        } else {
            connect("/v1/envs/" + instanceId + "/step/", "POST",
                    "{\"instance_id\":\"" + instanceId + "\", \"action\":" + action + 
                    ", \"render\":" + Boolean.toString(render) + "}");
        }
        JSONObject jobj = getJson();
        
        return new StepObject(
        		jobj.get("observation"), jobj.getFloat("reward"), 
        		jobj.getBoolean("done"), jobj.get("info"));
    }
    public StepObject stepEnv1(String instanceId, double action, boolean isDiscreteSpace, boolean render) {
        if (isDiscreteSpace) {
            connect1("/v1/envs/" + instanceId + "/step/", "POST",
                    "{\"instance_id\":\"" + instanceId + "\", \"action\":" + (int) action + 
                    ", \"render\":" + Boolean.toString(render) + "}");
        } else {
            connect1("/v1/envs/" + instanceId + "/step/", "POST",
                    "{\"instance_id\":\"" + instanceId + "\", \"action\":" + action + 
                    ", \"render\":" + Boolean.toString(render) + "}");
        }
        JSONObject jobj = getJson1();
        return new StepObject(
                jobj.get("observation"), jobj.getFloat("reward"), 
                jobj.getBoolean("done"), jobj.get("info"));
    }

    /**
     * Gets the name and the dimensions of the environment's action space.
     * @param instanceId The id of the environment.
     * @return Whatever the action space of the environment is. Probably JSONObject.
     */
    public static Object actionSpace(String instanceId) {
        connect("/v1/envs/" + instanceId + "/action_space/", "GET", "{\"instance_id\":\"" + instanceId + "\"}");
        return getJson().get("info");
    }
    public Object actionSpace1(String instanceId) {
        connect1("/v1/envs/" + instanceId + "/action_space/", "GET", "{\"instance_id\":\"" + instanceId + "\"}");
        return getJson1().get("info");
    }
    
    /**
     * Gets the dimension from the JSONObject obtained from actionSpace.
     * @param jobj JSONObject from actionSpace.
     * @return Whether the space is discrete.
     */
    public static boolean isActionSpaceDiscrete(JSONObject jobj) {
        String name = jobj.getString("name");
        if(name.equals("Discrete")) {
            return true;
        }else {
            return false;
        }
    }
    public boolean isActionSpaceDiscrete1(JSONObject jobj) {
        String name = jobj.getString("name");
        if(name.equals("Discrete")) {
            return true;
        }else {
            return false;
        }
    }
    
    /**
     * Gets the size of the action space (number of distinct actions).
     * @param jobj JSONObject from actionSpace.
     * @return Size of actionSpace.
     */
    public static int actionSpaceSize(JSONObject jobj) {
        return jobj.getInt("n");
    }
    public int actionSpaceSize1(JSONObject jobj) {
        return jobj.getInt("n");
    }

    /**
     * *** I COULDN'T ACTUALLY GET THIS ONE TO WORK, MAYBE MY TEST ENVIRONMENT DOESN'T USE THIS? ***
     * Gets the name and the dimensions of the environment's observation space.
     * @param instanceId The id of the environment.
     * @return Whatever the observation space of the environment is.
     */
    public static void observationSpace(String instanceId) {
        connect("/v1/envs/" + instanceId + "/observation_space/", "GET", "{\"instance_id\":\"" + instanceId + "\"}");
        System.out.println(getJson().toString());
    }
    public void observationSpace1(String instanceId) {
        connect1("/v1/envs/" + instanceId + "/observation_space/", "GET", "{\"instance_id\":\"" + instanceId + "\"}");
        System.out.println(getJson1().toString());
    }

    /**
     * *** DIDN'T TEST! ***
     * Start monitoring.
     * @param instanceId The id of the environment.
     * @param force Whether to clear existing training data.
     * @param resume Keep data that's already in.
     */
    public static void startMonitor(String instanceId, boolean force, boolean resume) {
        connect("/v1/envs/" + instanceId + "/monitor/start/", "POST", "{\"instance_id\":\"" + instanceId
                + "\", \"force\":" + Boolean.toString(force) + ", \"resume\":" + Boolean.toString(resume) + "}");
    }

    /**
     * *** DIDN'T TEST! ***
     * Flush all monitor data to disk.
     * @param instanceId The id of the environment.
     */
    public static void closeMonitor(String instanceId) {
        connect("/v1/envs/" + instanceId + "/monitor/close/", "POST", "{\"instance_id\":\"" + instanceId + "\"}");
    }

    /**
     * *** DIDN'T TEST! ***
     * Probably uploads your thing to OpenAI? The method just said "Flush all monitor data to disk"
     * on the Gym HTTP API GitHub page, but it seems to do something different, I'm new to gym so I
     * don't really know.
     * @param trainingDir
     * @param apiKey
     * @param algId
     */
    public static void upload(String trainingDir, String apiKey, String algId) {
        connect("/v1/upload/", "POST", "{\"training_dir\":\"" + trainingDir + "\"," + "\"api_key\":\"" + apiKey + "\","
                + "\"algorithm_id\":\"" + algId + "\"}");
    }

    /**
     * *** COULDN'T GET IT TO WORK! ***
     * Attempts to shutdown the server.
     */
    public static void shutdownServer() {
        connect("/v1/shutdown/", "POST", null);
    }

    /**
     * Get JSON from the connection: technique from: 
     * https://stackoverflow.com/questions/11901831/how-to-get-json-object-from-http-request-in-java
     * @return The JSON obtained from the connection, first line only, which should hopefully
     * contain all JSON.
     */
    private static JSONObject getJson() {
        JSONObject json = null;
        try {
            Scanner scanner = new Scanner(con.getInputStream());
            String response = scanner.useDelimiter("\\Z").next();
            json = new JSONObject(response);
            scanner.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return json;
    }
    private JSONObject getJson1() {
        JSONObject json = null;
        try {
            Scanner scanner = new Scanner(con1.getInputStream());
            String response = scanner.useDelimiter("\\Z").next();
            if(!response.startsWith("{")) {
                System.out.println(response);
            }
            json = new JSONObject(response);
            scanner.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return json;
    }

    /**
     * Does either a post or get request on the base url + urlEx. Learned from:
     * https://www.mkyong.com/java/how-to-send-http-request-getpost-in-java/ .
     * @param urlEx The extension to add onto base url.
     * @param mthd POST or GET.
     * @param args What to pass for a Post request, make null if not used.
     */
    private static void connect(String urlEx, String mthd, String args) {
        try {
            URL url = new URL(baseUrl + urlEx);
            con = (HttpURLConnection) (url).openConnection();
            con.setRequestMethod(mthd);
            if (mthd.equals("POST")) {
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/json");
                con.setRequestProperty("Accept", "application/json");
                DataOutputStream wr = new DataOutputStream(con.getOutputStream());
                wr.writeBytes(args);
                wr.flush();
                wr.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void connect1(String urlEx, String mthd, String args) {
        try {
            URL url = new URL(baseUrl + urlEx);
            //start = System.nanoTime();
            con1 = (HttpURLConnection) (url).openConnection();
            //System.out.println("Open Connection Time: " + (System.nanoTime() - start));
            //start = System.nanoTime();
            con1.setRequestMethod(mthd);
            //System.out.println("Request Method Time: " + (System.nanoTime() - start));
            if (mthd.equals("POST")) {
                con1.setDoOutput(true);
                con1.setRequestProperty("Content-Type", "application/json");
                con1.setRequestProperty("Accept", "application/json");
                //start = System.nanoTime();
                DataOutputStream wr = new DataOutputStream(con1.getOutputStream());
                //System.out.println("Output Stream Time: " + (System.nanoTime() - start));
                wr.writeBytes(args);
                wr.flush();
                wr.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Same as connect method but with prints for debugging. 
     */
    @SuppressWarnings("unused")
	private static void connectDebug(String urlEx, String mthd, String args) {
        try {
            URL url = new URL(baseUrl + urlEx);
            con = (HttpURLConnection) (url).openConnection();
            con.setRequestMethod(mthd);
            if (mthd.equals("GET")) {
                int responseCode = con.getResponseCode();
                System.out.println("\nSending 'GET' request to URL : " + url);
                System.out.println("Response Code : " + responseCode);
            } else { // post
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/json");
                con.setRequestProperty("Accept", "application/json");
                DataOutputStream wr = new DataOutputStream(con.getOutputStream());
                wr.writeBytes(args);
                wr.flush();
                wr.close();

                int responseCode = con.getResponseCode();
                System.out.println("\nSending 'POST' request to URL : " + url);
                System.out.println("Post parameters : " + args);
                System.out.println("Response Code : " + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
