package agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONObject;

import javaclient.GymJavaHttpClient;
import sbbj_tpg.TPGAlgorithm;
import sbbj_tpg.TPGLearn;
import sbbj_tpg.Team;

public class TpgAgentParallel {
    
    public static enum LevelType{SingleLevel, FiveLevel};
    public static enum TrainType{DeathSequence, AllLevels, LevelPerGen, MultiGame};
    
    // default values for arguments
    public static int port = 5000;
    public static boolean render = false;
    public static boolean wtf = false;
    public static String game = "aliens";
    public static int generations = 100000;
    public static LevelType levelType = LevelType.SingleLevel;
    public static TrainType trainType = TrainType.MultiGame;
    public static int maxSteps = 10; // max steps before game quits
    public static int maxStepRec = 10; // max amount of step recordings to take
    public static int bestStepRec = 5; // max amount of step recordings to take after fitness
    public static int stepRecDiff = 20; // number of frames to take off of step rec
    public static int defEps = 1; // default number of episodes per individual
    public static int defReps = 5; // default number of reps per set for sequences
    public static boolean quickie = false; // whether to do single frame episodes
    public static int threads = 5;
    public static int defLevel = 0;
    
    
    public static Random rand;
    
    
    // Stuff for parallelization
    public static int gen;
    public static TpgTeamThread[] teamThreads;
    public static TPGLearn tpg;
    public static int rep;
    public static Float[] fitnesses;
    public static LinkedList<String> gameQueue;
    public static HashMap<String,String[][]> lvlIds;
    public static HashMap<String,long[]> numsActions;
    public static String[] games;
    public static long[] curActions;
    public static int lvlIdx;
    public static int[] epLosses;
    public static ArrayList<ArrayList<Integer>> stepSeqs;
    public static int eps;
    public static String genSummaries = "";
    public static HashMap<Team,HashMap<String,Float>> rewardMap;
    
    private static long startTime;

    public static void main(String[] args) {
        
        System.out.println("Processors Available: " + Runtime.getRuntime().availableProcessors());
        
        for(String arg : args) {
            if(arg.toLowerCase().startsWith("port=")) {
                port = Integer.parseInt(arg.substring(5));
            }else if(arg.toLowerCase().startsWith("render=")) {
                render = Boolean.parseBoolean(arg.substring(7));
            }else if(arg.toLowerCase().startsWith("wtf=")) {
                wtf = Boolean.parseBoolean(arg.substring(4));
            }else if(arg.toLowerCase().startsWith("game=")) {
                game = arg.substring(5);
            }else if(arg.toLowerCase().startsWith("gens=") || arg.toLowerCase().startsWith("generations=")) {
                generations = Integer.parseInt(arg.substring(arg.indexOf("=") + 1));
            }else if(arg.toLowerCase().startsWith("levellype=") || arg.toLowerCase().startsWith("ltype=")) {
                String val = arg.substring(arg.indexOf("=") + 1);
                if(val.equals("1") || val.toLowerCase().equals("single")) {
                    levelType = LevelType.SingleLevel;
                }else{
                    levelType = LevelType.FiveLevel;
                }
            }else if(arg.toLowerCase().startsWith("traintype=") || arg.toLowerCase().startsWith("ttype=")) {
                String val = arg.substring(arg.indexOf("=") + 1);
                if(val.toLowerCase().equals("mg") || val.toLowerCase().equals("multi") || val.toLowerCase().equals("multigame")) {
                    trainType = TrainType.MultiGame;
                }else{
                    trainType = TrainType.DeathSequence; // probably won't ever use anything else
                }
            }else if(arg.toLowerCase().startsWith("maxsteps=")) {
                maxSteps = Integer.parseInt(arg.substring(arg.indexOf("=") + 1));
            }else if(arg.toLowerCase().startsWith("maxsteprec=")) {
                maxStepRec = Integer.parseInt(arg.substring(arg.indexOf("=") + 1));
            }else if(arg.toLowerCase().startsWith("beststeprec=")) {
                bestStepRec = Integer.parseInt(arg.substring(arg.indexOf("=") + 1));
            }else if(arg.toLowerCase().startsWith("steprecdiff=")) {
                stepRecDiff = Integer.parseInt(arg.substring(arg.indexOf("=") + 1));
            }else if(arg.toLowerCase().startsWith("defeps=")) {
                defEps = Integer.parseInt(arg.substring(arg.indexOf("=") + 1));
            }else if(arg.toLowerCase().startsWith("defreps=")) {
                defReps = Integer.parseInt(arg.substring(arg.indexOf("=") + 1));
            }else if(arg.toLowerCase().startsWith("quickie=")) {
                quickie = Boolean.parseBoolean(arg.substring(arg.indexOf("=") + 1));
            }else if(arg.toLowerCase().startsWith("threads=")) {
                threads = Integer.parseInt(arg.substring(arg.indexOf("=") + 1));
            }else if(arg.toLowerCase().startsWith("deflevel=")) {
                defLevel = Integer.parseInt(arg.substring(arg.indexOf("=") + 1));
            }
        }
        rand = new Random(55);
        
        System.out.println("Starting TPG on " + "port: " + port + ", render: " + render + ", wtf: " + wtf + ", game: " + game
                + ",\n gens: " + generations + ", leveltype: " + levelType.toString() + ", trainrype: " + trainType.toString()
                + ",\n maxsteps: " + maxSteps + ", maxsteprec: " + maxStepRec + ", beststeprec: " + bestStepRec + ", steprecdiff: " + stepRecDiff
                + ",\n defeps: " + defEps + ", defreps: " + defReps + ", quickie: " + quickie + ", threads: " + threads + ", deflevel: " + defLevel );
        
        setupTpgParallel();
    }

    public static void setupTpgParallel() {
        
        gen = 0;
        
        // write to file instead of console to record stuff if not debugging
        if(wtf) {
            if(trainType != TrainType.MultiGame) {
                TpgAgent.wtf(game);
            }else {
                TpgAgent.wtf("multigame");
            }
        }
        
        GymJavaHttpClient.baseUrl = "http://127.0.0.1:" + String.valueOf(port);
        
        // set up tpg agent
        TPGAlgorithm tpgAlg = new TPGAlgorithm("parameters.arg", "learn");
        tpg = TpgAgent.setupTpgLearn(6, tpgAlg); // 6 actions in gvgai
        
        games = game.split(",");
        lvlIds = getLevelsIdsParallel(games, levelType);
        numsActions = getNumsActionsMapParallel(lvlIds);
        
        gameQueue = getGameQueue(new ArrayList<String>(Arrays.asList(games)));
        
        startTime = System.currentTimeMillis();
        
        startNewGeneration();
    }
    
    public static void startNewGeneration() {

        gen++;
        System.out.println("==================================================");
        System.out.println("Starting Generation #" + gen);
        rep = (gen - 1) % defReps;
        fitnesses = new Float[tpg.remainingTeams()]; // track fitness of all individuals per gen
        
        rewardMap = new HashMap<Team, HashMap<String, Float>>();
        
        // choose new game and level maybe
        if(rep == 0) { 
            if(gameQueue.isEmpty()) {
                gameQueue = getGameQueue(new ArrayList<String>(Arrays.asList(games)));
            }
            game = gameQueue.removeFirst();
            curActions = numsActions.get(game); // actions for this game
            lvlIdx = rand.nextInt(lvlIds.get(game).length); // choose random level
            
            stepSeqs = new ArrayList<ArrayList<Integer>>();
        }
        
        System.out.println("On Game: " + game);
        System.out.println("On Level: " + lvlIdx);
        
        // used for  rep 1 of death sequence for point population fitness
        if(rep == 1) { // on rep 1 we use epLoss to find fitness of sequences
            epLosses = new int[stepSeqs.size()];
        }
        
        // eps is number of sequences we do, or just 1 if rep 0
        eps = defEps;
        if(rep > 0 && stepSeqs.size() > 0) {
            eps = stepSeqs.size(); // 1 episode per sequence
        }
        
        teamThreads = new TpgTeamThread[threads];
        // start threads on right levels
        for(int i = 0; i < threads; i++) {
            teamThreads[i] = new TpgTeamThread(new GymJavaHttpClient(), i); // put new thread in
            if(tpg.remainingTeams() > 0) {
                teamThreads[i].lvl = lvlIds.get(game)[lvlIdx][i];
                teamThreads[i].renew(tpg.getCurTeam());
            }
        }
        // start the threads
        for(int i = 0; i < threads; i++) {
            teamThreads[i].start();
        }
    }
    
    /**
     * 
     * @param id
     * @return Whether the calling thread should die.
     */
    public static synchronized boolean threadIsDone(int id) {
        reportTeamResult(teamThreads[id]);
        teamThreads[id].superDone = true;
        // check if still teams to run this generation
        if(tpg.remainingTeams() > 0) {
            teamThreads[id].renew(tpg.getCurTeam());
            return false;
        }else if(checkAllThreadsDone()) {
            endGeneration();
            if(gen < generations) { // start next generation
                tpg.selection();
                tpg.generateNewTeams();
                tpg.nextEpoch();
                startNewGeneration();
            }else { // totally done
                // stub
            }
        }
        return true;
    }
    
    public static boolean checkAllThreadsDone() {
        for(int i = 0; i < threads; i++) {
            if(!teamThreads[i].superDone) {
                return false;
            }
        }
        return true;
    }
    
    public static void reportTeamResult(TpgTeamThread thread) {
        fitnesses[thread.team.genId] = thread.score;
        System.out.println("Average Score: " + thread.score);
        System.out.println("That was for Team: " + thread.team.genId);
        System.out.println(" ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~");
    }
    
    public static HashMap<String,String[][]> getLevelsIdsParallel(String[] gameNames, LevelType levelType) {
        HashMap<String,String[][]> levelsIds = new HashMap<String,String[][]>();
        if(levelType == LevelType.FiveLevel) {
            for(String game : gameNames) {
                String[][] lvls = new String[5][threads];
                for(int lvl = 0; lvl < 5; lvl++) {
                    for(int thrd = 0; thrd < threads; thrd++) {
                        lvls[lvl][thrd] = GymJavaHttpClient.createEnv("gvgai-" + game + "-lvl" + lvl + "-v0");
                    }
                }
                levelsIds.put(game, lvls);
            }
        }else if(levelType == LevelType.SingleLevel) {
            for(String game : gameNames) {
                String[][] lvls = new String[1][threads];
                for(int thrd = 0; thrd < threads; thrd++) {
                    lvls[0][thrd] = GymJavaHttpClient.createEnv("gvgai-" + game + "-lvl" + defLevel + "-v0");
                }
                levelsIds.put(game, lvls);
            }
        }
        
        return levelsIds;
    }
    
    public static HashMap<String,long[]> getNumsActionsMapParallel(HashMap<String,String[][]> lvlIds) {
        Set<String> games = lvlIds.keySet();
        HashMap<String,long[]> numsActions = new HashMap<String,long[]>();
        for(String game : games) {
            // get actionspace of first level in each game
            long[] actions = new long[GymJavaHttpClient.actionSpaceSize((JSONObject)GymJavaHttpClient.actionSpace(lvlIds.get(game)[0][0]))];
            for(int i = 0; i < actions.length; i++) {
                actions[i] = (long)i;
            }
            numsActions.put(game, actions);
        }
        // heyyyyyyyyyy
        return numsActions;
    }
    
    public static LinkedList<String> getGameQueue(ArrayList<String> keys) {
        Collections.shuffle(keys);
        return new LinkedList<String>(keys);
    }

    public static void endGeneration() {
        if(rep == 0 && stepSeqs.size() > 20) {
            // take only up a certain amount
            try {
                Collections.shuffle(stepSeqs); // randomize
                stepSeqs = new ArrayList<ArrayList<Integer>>(stepSeqs.subList(0, maxStepRec));
            }catch(Exception e) {
                System.out.println("Oof!");
            }
            System.out.println("Taking " + stepSeqs.size() + " step recordings into next generation");
        }else if(rep == 1) {
            // get fitness of the step sequences
            // first find max losses
            int max = 0;
            for(int i = 0; i < epLosses.length; i++) {
                if(epLosses[i] > max) {
                    max = epLosses[i];
                }
            }
            // now get fitnesses of each sequence
            float[] fitness = new float[epLosses.length];
            float lambda = 0.75f; // weight parameter for fitness
            for(int i = 0; i < epLosses.length; i++) {
                fitness[i] = (float)((2*((float)epLosses[i]/max)/lambda) -
                        (Math.pow(((float)epLosses[i]/max)/lambda, 2)));
            }
            // bubble sort with indexes
            int[] idxs = new int[fitness.length]; // index of fitnesses
            for(int i = 0; i < idxs.length; i++) {
                idxs[i] = i;
            }
            float swp;
            int swpi;
            for(int i = 0; i < fitness.length - 1; i++) {
                for(int j = 0; j < fitness.length - i - 1; j++) {
                    if(fitness[j+1] > fitness[j]) {
                        swp = fitness[j];
                        swpi = idxs[j];
                        
                        fitness[j] = fitness[j+1];
                        idxs[j] = idxs[j+1];
                        
                        fitness[j+1] = swp;
                        idxs[j] = swpi;
                    }
                }
            }
            ArrayList<ArrayList<Integer>> stepSeqsCopy = stepSeqs;
            stepSeqs = new ArrayList<ArrayList<Integer>>();
            // keep only the sequences that are good enough
            int taken = 0;
            for(int j = 0; j < idxs.length; j++) {
                int i = idxs[j];
                if(fitnesses.length - epLosses[i] > 0 && taken < bestStepRec) {
                    stepSeqs.add(stepSeqsCopy.get(i));
                    taken++;
                }else if(taken >= bestStepRec) {
                    break;
                }
            }
        }
        
        genSummaries += game + ": " + TpgAgent.getSummary(fitnesses);
        System.out.println("Fitness Summary:\n" + genSummaries);
        
        applyRewards(); // give rewards
        

        System.out.println("Ellapsed Seconds: " + (long)(System.currentTimeMillis() - startTime)/1000);
    }
    
    // give reward here instead of in individual threads for concurrency sake.
    public static void applyRewards() {
        for(Team team : rewardMap.keySet()) {
            for(String label : rewardMap.get(team).keySet()) {
                tpg.reward(team, label, rewardMap.get(team).get(label));
            }
        }
    }

    public static void fixSequence(ArrayList<ArrayList<Integer>> stepSeqs, Object info) {
        int stepSeqEnd = stepSeqs.size()-1; // last index
        
        if(stepSeqs.get(stepSeqEnd).size() > stepRecDiff) { // chop off end from sequence
            stepSeqs.set(stepSeqEnd, new ArrayList<Integer>(
                    stepSeqs.get(stepSeqEnd).subList(
                            0, stepSeqs.get(stepSeqEnd).size()-stepRecDiff)));
        }else if(stepSeqs.get(stepSeqEnd).size() <= stepRecDiff) { // sequence from start
            stepSeqs.set(stepSeqEnd, new ArrayList<Integer>());
        }else if(stepSeqs.get(stepSeqEnd).size() == maxSteps || TpgAgent.didIWin(info)) { // remove if win or end
            stepSeqs.remove(stepSeqEnd);
        }
    }
}
