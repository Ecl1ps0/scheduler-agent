package com.prod_mas.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.prod_mas.utils.ExcelWriter;

import java.util.Map.Entry;

public class Scheduler extends Agent {
    private final Queue<String> pendingTasks = new LinkedList<>();
    private final Map<AID, String> workerTaskMap = new HashMap<>();
    private final Map<AID, Instant> workerHeartbeatMap = new HashMap<>();
    private final Map<AID, BigDecimal> workloadMap = new HashMap<>();
    private final Map<String, List<Double>> modelTimingData = new HashMap<>();
    private final int HEALTH_CHECK_TIMEOUT = 30;
    private final int HEALTH_CHECK_INTERVAL = 5000;
    private Instant firstAssignmentTime = null;
    private Instant allTasksCompletedTime = null;

    @Override
    protected void setup() {

        String host = System.getProperty("MAIN_HOST");

        String[] models = new String[] {
            "catboost_model.exe",
            "random_forest.exe",
            "xgboost.exe"
        };

        int TOTAL_TASKS = 100;

        Map<String, Integer> modelCounts = new HashMap<>();
        for (String m : models) {
            modelCounts.put(m, 0);
        }

        for (int i = 0; i < TOTAL_TASKS; i++) {
            String model = models[i % models.length];
            String url = String.format("http://%s:8080/files/%s", host, model);
            pendingTasks.add(url);
            modelCounts.put(model, modelCounts.get(model) + 1);
        }

        System.out.println("Loaded " + pendingTasks.size() + " tasks into queue!");

        System.out.println("Model distribution:");
        for (Map.Entry<String, Integer> entry : modelCounts.entrySet()) {
            System.out.println(entry.getKey() + " → " + entry.getValue());
        }

        for (String m : models) {
            modelTimingData.put(m, new ArrayList<>());
        }

        addBehaviour(new TickerBehaviour(this, 10000) {
            @Override
            protected void onTick() {
                if (pendingTasks.isEmpty()) {
                    return;
                }

                try {
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("model-trainer");
                    template.addServices(sd);

                    DFAgentDescription[] result = DFService.search(myAgent, template);

                    if (result.length == 0) {
                        System.out.println("No workers available!");
                        return;
                    }

                    workloadMap.clear();

                    for (DFAgentDescription desc : result) {
                        AID worker = desc.getName();

                        // Only request workload if worker is free
                        if (!workerTaskMap.containsKey(worker)) {
                            ACLMessage req = new ACLMessage(ACLMessage.QUERY_IF);
                            req.addReceiver(worker);
                            req.setContent("Workload?");
                            send(req);
                        }
                    }

                } catch (FIPAException fe) {
                    fe.printStackTrace();
                }
            }
        });

        // 2) Collect replies and assign tasks
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {

                    AID sender = msg.getSender();

                    switch (msg.getPerformative()) {
                        case ACLMessage.INFORM:
                            try {
                                BigDecimal load = new BigDecimal(msg.getContent());
                                workloadMap.put(sender, load);
                                workerHeartbeatMap.put(sender, Instant.now());

                                System.out.println("Worker " + sender.getLocalName() + " load=" + load);

                                // Assign task if worker is free
                                if (!workerTaskMap.containsKey(sender) && !pendingTasks.isEmpty()) {
                                    if (firstAssignmentTime == null) {
                                        firstAssignmentTime = Instant.now();
                                    }
                                    
                                    String taskToAssign = pendingTasks.poll();
                                    ACLMessage assign = new ACLMessage(ACLMessage.REQUEST);
                                    assign.addReceiver(sender);
                                    assign.setContent(taskToAssign);
                                    send(assign);

                                    workerTaskMap.put(sender, taskToAssign);
                                    workerHeartbeatMap.put(sender, Instant.now());

                                    System.out.println("Assigned task " + taskToAssign + " to " + sender.getLocalName());
                                }

                            } catch (NumberFormatException ignored) {
                                System.out.println("Invalid workload reply: " + msg.getContent());
                            }
                            break;

                        case ACLMessage.CONFIRM:
                            // Worker completed a task
                            String[] parts = msg.getContent().split("\\|");

                            String completedTask = parts[0];
                            double durationMs = Double.parseDouble(parts[1]);

                            workerTaskMap.remove(sender);
                            workerHeartbeatMap.remove(sender);

                            String modelName = completedTask.substring(completedTask.lastIndexOf('/') + 1);
                            modelTimingData.get(modelName).add(durationMs);

                            System.out.println(
                                "Worker " + sender.getLocalName() +
                                " completed task " + modelName +
                                " in " + durationMs + " ms"
                            );

                            ExcelWriter.appendTrainingResult("training_results.xlsx", modelName, durationMs);

                            if (pendingTasks.isEmpty() && workerTaskMap.isEmpty()) {
                                allTasksCompletedTime = Instant.now();
                                long seconds = Duration.between(firstAssignmentTime, allTasksCompletedTime).toSeconds();

                                System.out.println("All tasks completed!");
                                System.out.println("Total processing time: " + seconds + " seconds");

                                System.out.println("\n=== Average times per model ===");

                                for (var e : modelTimingData.entrySet()) {
                                    List<Double> times = e.getValue();
                                    double avg = (long) times.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                                    System.out.println(e.getKey() + " → " + avg + " ms");
                                }
                            }
                            break;
                    }
                }
            }
        });

        // 3) Periodic health check for workers that are currently busy
        addBehaviour(new TickerBehaviour(this, HEALTH_CHECK_INTERVAL) {
            @Override
            protected void onTick() {
                Instant now = Instant.now();
                List<AID> workersToRemove = new ArrayList<>();

                for (Map.Entry<AID, String> entry : workerTaskMap.entrySet()) {
                    AID worker = entry.getKey();
                    String task = entry.getValue();
                    Instant lastHeartbeat = workerHeartbeatMap.getOrDefault(worker, Instant.EPOCH);

                    if (now.getEpochSecond() - lastHeartbeat.getEpochSecond() > HEALTH_CHECK_TIMEOUT) {
                        // Worker unresponsive → reassign task
                        System.out.println("Worker " + worker.getLocalName() + " unresponsive. Reassigning task " + task);
                        pendingTasks.add(task);
                        workersToRemove.add(worker);
                        workerHeartbeatMap.remove(worker);
                    } else {
                        // Send heartbeat check
                        ACLMessage heartbeat = new ACLMessage(ACLMessage.QUERY_IF);
                        heartbeat.addReceiver(worker);
                        heartbeat.setContent("Are you alive?");
                        send(heartbeat);
                    }
                }

                // Remove unresponsive workers from task map
                for (AID w : workersToRemove) {
                    workerTaskMap.remove(w);
                }
            }
        });
    }
}