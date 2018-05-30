package agent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONObject;

import javaclient.GymJavaHttpClient;
import javaclient.StepObject;
import sbbj_tpg.*;

public class TpgAgent {
    
    @SuppressWarnings("unused")
    public static void main(String[] args) {
        
        final int MAX_STEPS = 1250; // max steps before game quits
        
        if(true) {
            try {
                // https://stackoverflow.com/questions/7488559/current-timestamp-as-filename-in-java
                String fileName = new SimpleDateFormat("yyyyMMddHHmm'.txt'").format(new Date());
                //throw new FileNotFoundException();
                PrintStream pToFile = new PrintStream(new File("/home/ryan/tpg-playing-records/seaquest/" + fileName));
                System.setOut(pToFile);
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        
        GymJavaHttpClient.baseUrl = "http://127.0.0.1:5000";
        
        // agent variables
        TPGAlgorithm tpgAlgorithm;
        TPGLearn tpg;
        
        // set up client
        String[] lvlIds = null;
        int config = 0; // to decide what levels to do
        if(config == 0) {
            lvlIds = new String[] { // get levels
                GymJavaHttpClient.createEnv("gvgai-seaquest-lvl0-v0"),
                GymJavaHttpClient.createEnv("gvgai-seaquest-lvl1-v0"),
                GymJavaHttpClient.createEnv("gvgai-seaquest-lvl2-v0"),
                GymJavaHttpClient.createEnv("gvgai-seaquest-lvl3-v0"),
                GymJavaHttpClient.createEnv("gvgai-seaquest-lvl4-v0")};
        }else if(config == 1) {
            lvlIds = new String[] { // get levels
                    GymJavaHttpClient.createEnv("gvgai-aliens-lvl0-v0")};
        }
        // get action space of levels
        int[] lvlActionsNums = new int[lvlIds.length];
        for(int i = 0; i < lvlIds.length; i++) {
            lvlActionsNums[i] = GymJavaHttpClient.actionSpaceSize((JSONObject)GymJavaHttpClient.actionSpace(lvlIds[0]));
        }
        
        // setup tpg
        tpgAlgorithm = new TPGAlgorithm("parameters.arg", "learn");
        tpg = tpgAlgorithm.getTPGLearn();
        long[] tpgActions = new long[lvlActionsNums[0]];
        for(long i = 0; i < lvlActionsNums[0]; i++) {
            tpgActions[(int) i] = i;
        }
        tpg.setActions(tpgActions);
        tpg.initialize();
        
        String fitSummary = new String(); // to track fitness over generations
        
        boolean isDeathSequence = true; // whether to train on death or plateau sequences.
        int seqReps = 6; // number of generations to cycle through sequences as described below
        // if gen%seqReps == 0: find death/plateau sequences
        // if gen%seqReps == 1: evaluate death/plateau sequences
        // if gen%seqReps >  1: perform on chosen death/plateau sequences
        ArrayList<ArrayList<Integer>> stepRec = new ArrayList<ArrayList<Integer>>();
        
        for(int generation = 0; generation < 50000; generation++) { // generation iter
            System.out.println("Starting Generation #" + generation);
            int lvl = generation % lvlIds.length;
            String lvlId = lvlIds[lvl];
            System.out.println("On Level " + lvl);
            Integer[] fitnesses = new Integer[tpg.remainingTeams()]; // to track fitnesses in this generation
            int curRep = generation%seqReps; // cur rep in sequence reps
            // count wins and losses of each episode in rep 1
            int[] epWins = null;
            int[] epLosses = null;
            if(curRep == 1) {
                epWins = new int[stepRec.size()];
                epLosses = new int[stepRec.size()];
            }
            System.out.println("stepRec.size() is " + stepRec.size());
            
            int episodes; // default 1, will be mapped to number of sequences
            if(stepRec.size() == 0) {
                episodes = 1;
            }else {
                episodes = stepRec.size();
            }
            
            while(tpg.remainingTeams() > 0) { // team iter
                System.out.println("Remaining Teams: " + tpg.remainingTeams());
                float reward = 0; // average reward over 5 episodes
                long[] ac = new long[lvlActionsNums[lvl]]; // action count
                
                for(int episode = 0; episode < episodes; episode++) { // episodes per team
                    System.out.println("starting episode #" + episode);
                    Object obs = GymJavaHttpClient.resetEnv(lvlId); // reset the environment
                    Boolean isDone = false; // whether current episode is done
                    int action; // action for agent to do
                    int stepC = 0; // stepCounter
                    boolean isAutopilot = true;
                    System.out.println("autopilot control");
                    Object info = null;
                    while(!isDone) { // do steps
                        stepC++;
                        if(curRep == 0 || stepRec.size() == 0 
                                || stepC > stepRec.get(episode).size()) { // initial rep or finished with autopilot
                            if(isAutopilot) {
                                isAutopilot = false;
                                System.out.println("tpg control");
                            }
                            action = (int) tpg.participate(getFeatures(obs)); // tpg chooses
                            if(curRep == 0) {
                                if(stepC == 1) {
                                    stepRec.add(new ArrayList<Integer>());
                                }
                                stepRec.get(stepRec.size()-1).add(action);
                            }
                        }else {
                            action = stepRec.get(episode).get(stepC-1);
                        }
                        ac[action] += 1; // track actions
                        StepObject step = GymJavaHttpClient.stepEnv(lvlId, action, true, false);
                        obs = step.observation;
                        isDone = step.done;
                        if(!isAutopilot) {
                            reward += step.reward;
                        }
                        info = step.info;
                    }
                    System.out.println(info.toString());
                    // save all but last 50 of sequence if died in rep 0
                    if(curRep == 0) {
                        if(stepRec.get(stepRec.size()-1).size() > 50) {
                            stepRec.set(stepRec.size()-1, new ArrayList<Integer>(stepRec.get(stepRec.size()-1).subList(0, stepRec.get(stepRec.size()-1).size()-50)));
                        }else if(stepRec.get(stepRec.size()-1).size() <= 50) {
                            stepRec.set(stepRec.size()-1, new ArrayList<Integer>());
                        }else if(stepRec.get(stepRec.size()-1).size() == MAX_STEPS ||
                                didIWinThisCurrentGameWowThisIsAVeryLongFunctionNameShouldIStopHereNopePlsNoItGoesOnAndOnAndOnLikeThatSong(info)) {
                            stepRec.remove(stepRec.size()-1);
                        }
                    }else if(curRep == 1) {
                        if(didIWinThisCurrentGameWowThisIsAVeryLongFunctionNameShouldIStopHereNopePlsNoItGoesOnAndOnAndOnLikeThatSong(info)) { // survived
                            epWins[episode]++;
                        }else {
                            epLosses[episode]++;
                        }
                    }
                }
                
                reward /= episodes;
                
                // print actions taken
                System.out.print("Action Count: ");
                for(int i = 0; i < ac.length; i++) {
                    System.out.print(ac[i] + " ");
                }
                System.out.println();
                
                System.out.println("Score: " + reward); // print the score
                
                fitnesses[fitnesses.length - tpg.remainingTeams()] = (int)reward;
                tpg.reward("game", reward); // apply reward
            }
            if(curRep == 0 && stepRec.size() > 20) {
                // take only 20 up to
                try {
                    stepRec = new ArrayList<ArrayList<Integer>>(stepRec.subList(0, 20));
                }catch(Exception e) {
                    System.out.println(e);
                    e.printStackTrace();
                }
            }else if(curRep == 1) {
                // see which sequences are divisive enough
                for(int i = epWins.length - 1; i >= 0; i--) {
                    if(!(epWins[i] != 0 && 
                            ((float)epLosses[i]/(float)epWins[i] > 0.11 && 
                                    (float)epLosses[i]/(float)epWins[i] < epLosses[i] + epWins[i] - 1))) {
                        stepRec.remove(i); // not goldilocks
                    }
                }
            }
            
            fitSummary += getSummary(fitnesses);
            System.out.println("Fitness Summary:\n" + fitSummary);
            // prep next generation
            tpg.selection();
            tpg.generateNewTeams();
            tpg.nextEpoch();
        }
    }
    
    public static boolean didIWinThisCurrentGameWowThisIsAVeryLongFunctionNameShouldIStopHereNopePlsNoItGoesOnAndOnAndOnLikeThatSong(Object info) {
        JSONObject jinfo = (JSONObject)info;
        return jinfo.getString("winner").equals("PLAYER_WINS");
    }
    
    public static double[] getFeatures(Object obs) {
        int res = 64; // x and y resolution of screen space
        double[] features = new double[res*res];
        JSONArray jObs = (JSONArray)obs;
        boolean first = true; // for getting column size first time only
        int fcounter = 0; // counter for feature array
        
        int rowSz = (int) Math.ceil((float)jObs.length()/(float)res); // reduced row size
        int columnSz = 1; // reduced column size (found in first row and column)
        
        // the pixel coords
        int pRow = -1;
        int pCol = -1;
        
        Iterator<Object> iterRow = jObs.iterator(); // row iterator
        while(iterRow.hasNext()) { // iterates rows
            pRow++;
            JSONArray theNext = (JSONArray) iterRow.next();
            if((int)(pRow - rowSz/2) % rowSz != 0) {
                // not in row with features
                continue;
            }
            if(first) {
                // get column size only first time
                columnSz = (int) Math.ceil((float)((JSONArray)theNext).length()/(float)res);
                first = false;
            }
            pCol = -1;
            Iterator<Object> iterCol = theNext.iterator();
            while(iterCol.hasNext()) { // iterates columns
                pCol++;
                Iterator<Object> iterPixel = ((JSONArray)iterCol.next()).iterator();
                if((int)(pCol-columnSz/2) % columnSz != 0) {
                    // not in column with feature
                    continue;
                }
                int r,g,b;
                r = (int)iterPixel.next();
                g = (int)iterPixel.next();
                b = (int)iterPixel.next();
                features[fcounter] = (r << 16) + (g << 8) + b;
                fcounter++;
            }
        }
        
        return features;
    }
    
    public static String getSummary(Integer[] fitnesses) {
        return Collections.min(Arrays.asList(fitnesses)).toString() + ","
                + Collections.max(Arrays.asList(fitnesses)).toString() + ","
                + getAvg(fitnesses).toString() + "\n";
    }
    
    public static Integer getAvg(Integer[] arr) {
        int sum = 0;
        for(int i = 0; i < arr.length; i++) {
            sum += arr[i];
        }
        
        return sum/arr.length;
    }
}
