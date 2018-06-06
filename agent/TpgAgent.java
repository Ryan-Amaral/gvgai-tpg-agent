package agent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONObject;

import javaclient.GymJavaHttpClient;
import javaclient.StepObject;
import sbbj_tpg.*;

public class TpgAgent {

    public static final int MAX_STEPS = 1250; // max steps before game quits
    public static final int MAX_STEPREC = 20; // max amount of step recordings to take
    public static final int STEPREC_DIF = 50; // number of frames to take off of step rec
    public static final int DEF_EPS = 1; // default number of episodes per individual
    public static final int DEF_REPS = 6; // default number of reps per set for sequences
    public static final boolean QUICKIE = false; // whether to do single frame episodes
    
    public static enum LevelType{SingleLevel, FiveLevel};
    public static enum TrainType{DeathSequence, AllLevels, LevelPerGen, MultiGame};

    public static void main(String[] args) {
        HashMap<String, String> argMap = new HashMap<String, String>();
        // default values
        argMap.put("port", "5000");
        argMap.put("game", "butterflies");
        for(String arg : args) {
            if(arg.startsWith("port=")) {
                argMap.put("port", arg.substring(5));
            }else if(arg.startsWith("game=")) {
                argMap.put("game", arg.substring(5));
            }
        }
        try {
            runTpg(Integer.parseInt(argMap.get("port")), true, argMap.get("game"), false, 10000, LevelType.FiveLevel, TrainType.DeathSequence);
        }catch(Exception e){
            System.out.println("Wrong!!! Do this: \"java -jar name.jar <game>\"");
        }
    }
    
    public static void runTpg(int port, boolean wtf, String gameName, boolean render, 
            int generations, LevelType levelType, TrainType trainType) {
        
        // write to file instead of console to record stuff
        if(wtf) {
            wtf(gameName);
        }
        
        GymJavaHttpClient.baseUrl = "http://127.0.0.1:" + String.valueOf(port);
        
        String[] lvlIds = getLevelIds(gameName, levelType); // store IDs of create level environments
        
        // number of action that can be performed in the environment
        long[] numsActions = getNumsActions(lvlIds);
        
        // set up tpg agent
        TPGAlgorithm tpgAlg = new TPGAlgorithm("parameters.arg", "learn");
        TPGLearn tpg = setupTpgLearn(6, tpgAlg); // 6 actions in gvgai
        
        runGenerations(generations, trainType, render, lvlIds, numsActions, tpg);
    }
    
    public static void wtf(String gameName) {
        try {
            // https://stackoverflow.com/questions/7488559/current-timestamp-as-filename-in-java
            String fileName = new SimpleDateFormat("yyyyMMddHHmm'.txt'").format(new Date());
            //throw new FileNotFoundException();
            PrintStream pToFile = new PrintStream(
                    new File("/home/ryan/tpg-playing-records/" + gameName + "/" + fileName));
            System.setOut(pToFile);
        } catch (FileNotFoundException e) {
            try {
                // https://stackoverflow.com/questions/7488559/current-timestamp-as-filename-in-java
                String fileName = new SimpleDateFormat("yyyyMMddHHmm'.txt'").format(new Date());
                //throw new FileNotFoundException();
                PrintStream pToFile = new PrintStream(
                        new File("/home/amaral/tpg-playing-records/" + gameName + "/" + fileName));
                System.setOut(pToFile);
            } catch (FileNotFoundException e1) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    
    public static String[] getLevelIds(String gameName, LevelType levelType) {
        String[] lvlIds = new String[] {};
        if(levelType == LevelType.FiveLevel) {
            lvlIds =  new String[] {
                GymJavaHttpClient.createEnv("gvgai-" + gameName + "-lvl0-v0"),
                GymJavaHttpClient.createEnv("gvgai-" + gameName + "-lvl1-v0"),
                GymJavaHttpClient.createEnv("gvgai-" + gameName + "-lvl2-v0"),
                GymJavaHttpClient.createEnv("gvgai-" + gameName + "-lvl3-v0"),
                GymJavaHttpClient.createEnv("gvgai-" + gameName + "-lvl4-v0")};
        }else if(levelType == LevelType.SingleLevel) {
            lvlIds =  new String[] {
                GymJavaHttpClient.createEnv("gvgai-" + gameName + "-lvl0-v0")};
        }
        
        return lvlIds;
    }
    
    public static long[] getNumsActions(String[] lvlIds) {
        long[] numsActions = new long[lvlIds.length];
        for(int i = 0; i < lvlIds.length; i++) {
            numsActions[i] = 
                    (long)GymJavaHttpClient.actionSpaceSize((JSONObject)GymJavaHttpClient.actionSpace(lvlIds[i]));
        }
        
        return numsActions;
    }
    
    public static TPGLearn setupTpgLearn(int numActions, TPGAlgorithm tpgAlg) {
        TPGLearn tpg = tpgAlg.getTPGLearn();
        long[] tpgActions = new long[numActions];
        for(long i = 0; i < numActions; i++) {
            tpgActions[(int) i] = i;
        }
        tpg.setActions(tpgActions);
        tpg.initialize();
        
        return tpg;
    }
    
    public static void runGenerations(int gens, TrainType trainType, 
            boolean render, String[] lvlIds, long[] numsActions, TPGLearn agent) {
        
        if(trainType == TrainType.DeathSequence) {
            runGenerationsDeathSequence(gens, render, lvlIds, numsActions, agent);
        }
    }
    
    public static void runGenerationsMultiGame() {
        
    }
    
    public static void runGenerationsDeathSequence(int gens, boolean render, 
            String[] lvlIds, long[] numsActions, TPGLearn agent){
        
        String genSummaries = new String(); // performance of each generation (min, max, avg)
        // how many reps in a sequence (for death sequence training)
        // rep 0 : each individual plays full sequence, save those that agent loses in (not oot)
        // rep 1 : each individual plays all saved sequences to decide which are better for training
        // rep 2+: each individual plays all good training sequences
        int seqReps = DEF_REPS;
        
        // record steps (which are sequences)
        ArrayList<ArrayList<Integer>> stepSeqs = new ArrayList<ArrayList<Integer>>();
        
        int lvl = -1;
        for(int gen = 0; gen < gens; gen++) {
            System.out.println("==================================================");
            System.out.println("Starting Generation #" + gen);
            
            Float[] fitnesses = new Float[agent.remainingTeams()]; // track fitness of all individuals per gen
            
            int rep = gen % seqReps; // rep changes every generation
            if(rep == 0) {
                lvl = (lvl+1)%lvlIds.length;
            }
            String lvlId = lvlIds[lvl];
            System.out.println("On level: " + lvl);
            
            // used for  rep 1 of death sequence
            int[] epWins = null;
            int[] epLosses = null;
            
            if(rep == 1) { // on rep 1 we use epVars to find discrimination of sequnces
                epWins = new int[stepSeqs.size()];
                epLosses = new int[stepSeqs.size()];
            }
            
            int eps = DEF_EPS; // default number of episodes
            if(rep > 0 && stepSeqs.size() > 0) {
                eps = stepSeqs.size(); // 1 episode per sequence
            }
            
            while(agent.remainingTeams() > 0) { // iterate through teams
                System.out.println(" ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~");
                System.out.println("Remaining Teams: " + agent.remainingTeams());
                float reward = 0;
                long[] ac = new long[(int) numsActions[lvl]]; // action count
                
                for(int ep = 0; ep < eps; ep++) { // iterate through episodes
                    float rwd = 0;
                    System.out.println("starting episode #" + ep);
                    Object obs = GymJavaHttpClient.resetEnv(lvlId); // reset the environment
                    Boolean isDone = false; // whether current episode is done
                    int action; // action for agent to do
                    int stepC = 0; // stepCounter
                    boolean isAutopilot = true; // for following sequence
                    System.out.println("autopilot control");
                    Object info = null;
                    
                    while(!isDone) { // iterate through environment
                        stepC++;
                        if(rep == 0 || stepSeqs.size() == 0 
                                || stepC > stepSeqs.get(ep).size()) { // finished with autopilot, do tpg
                            if(isAutopilot) {
                                isAutopilot = false;
                                System.out.println("tpg control");
                            }
                            action = (int) agent.participate(getFeatures(obs), new long[] {0,1,2,3}); // tpg chooses
                            
                            // record steps if rep 0, new sequence on first step
                            if(rep == 0) {
                                if(stepC == 1) {
                                    stepSeqs.add(new ArrayList<Integer>());
                                }
                                stepSeqs.get(stepSeqs.size()-1).add(action);
                            }
                        }else{ // do autopilot, until finish sequence
                            action = stepSeqs.get(ep).get(stepC-1);
                        }
                        ac[action] += 1; // track actions
                        StepObject step = GymJavaHttpClient.stepEnv(lvlId, action, true, render);
                        obs = step.observation;
                        isDone = step.done;
                        if(!isAutopilot) {
                            rwd += step.reward;
                        }
                        info = step.info;
                        if(QUICKIE) {
                            break;
                        }
                    } // episode done
                    reward += rwd;
                    
                    boolean nextTeam = false;
                    if(ep == eps - 1) {
                        nextTeam = true;
                    }
                    agent.reward("ep" + Integer.toString(ep), rwd, nextTeam); // apply reward
                    
                    if(rep == 0) { // In rep 0 take sequences of losses
                        fixSequence(stepSeqs, info);
                    }else if(rep == 1) { // record in win or lose this ep
                        if(didIWin(info)) {
                            epWins[ep]++;
                        }else {
                            epLosses[ep]++;
                        }
                    }
                }
                reward = reward / eps; // average rewards over eps
                // print actions taken
                System.out.print("Action Count: ");
                for(int i = 0; i < ac.length; i++) {
                    System.out.print(ac[i] + " ");
                }
                System.out.println("\nScore: " + reward); // print the score
                System.out.println();
                
                fitnesses[fitnesses.length - agent.remainingTeams() - 1] = reward;
            }
            
            if(rep == 0 && stepSeqs.size() > 20) {
                // take only 20 up to
                try {
                    Collections.shuffle(stepSeqs); // randomize
                    stepSeqs = new ArrayList<ArrayList<Integer>>(stepSeqs.subList(0, 20));
                }catch(Exception e) {
                    System.out.println(e);
                    e.printStackTrace();
                }
            }else if(rep == 1) {
                // see which sequences are divisive enough
                for(int i = epWins.length - 1; i >= 0; i--) {
                    if(!(epWins[i] != 0 && 
                            ((float)epLosses[i]/(float)epWins[i] > 0.11 && 
                                    (float)epLosses[i]/(float)epWins[i] < epLosses[i] + epWins[i] - 1))) {
                        stepSeqs.remove(i); // not goldilocks
                    }
                }
            }
            
            genSummaries += getSummary(fitnesses);
            System.out.println("Fitness Summary:\n" + genSummaries);
            // prep next generation
            agent.selection();
            agent.generateNewTeams();
            agent.nextEpoch();
        }
    }
    
    public static void fixSequence(ArrayList<ArrayList<Integer>> stepSeqs, Object info) {
        int stepSeqEnd = stepSeqs.size()-1; // last index
        
        if(stepSeqs.get(stepSeqEnd).size() > STEPREC_DIF) { // chop off 50 from sequence
            stepSeqs.set(stepSeqEnd, new ArrayList<Integer>(
                    stepSeqs.get(stepSeqEnd).subList(
                            0, stepSeqs.get(stepSeqEnd).size()-STEPREC_DIF)));
        }else if(stepSeqs.get(stepSeqEnd).size() <= STEPREC_DIF) { // sequence from start
            stepSeqs.set(stepSeqEnd, new ArrayList<Integer>());
        }else if(stepSeqs.get(stepSeqEnd).size() == MAX_STEPS || didIWin(info)) { // remove if win or end
            stepSeqs.remove(stepSeqEnd);
        }
    }
    
    public static boolean didIWin(Object info) {
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
    
    public static String getSummary(Float[] fitnesses) {
        return Collections.min(Arrays.asList(fitnesses)).toString() + ","
                + Collections.max(Arrays.asList(fitnesses)).toString() + ","
                + getAvg(fitnesses).toString() + "\n";
    }
    
    public static Float getAvg(Float[] arr) {
        float sum = 0;
        for(int i = 0; i < arr.length; i++) {
            sum += arr[i];
        }
        
        return sum/arr.length;
    }
}
