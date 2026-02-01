package net.minecraft.server;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Server-side performance profiler for tracking slow operations.
 * Admins can use commands to start/stop profiling and generate reports.
 * 
 * Usage:
 * - /profile start - Start profiling
 * - /profile stop - Stop profiling  
 * - /profile report - Show summary in console
 * - /profile save - Save detailed report to file
 * - /profile clear - Clear all profiling data
 */
public class ServerProfiler {
    private static final Logger log = Logger.getLogger("Minecraft");
    private static final ServerProfiler INSTANCE = new ServerProfiler();
    
    // Profiling state
    private volatile boolean enabled = false;
    private long profilingStartTime = 0;
    private long profilingEndTime = 0;
    
    // Threshold for "slow" calls in milliseconds
    private static final double SLOW_THRESHOLD_MS = 10.0;
    private static final double VERY_SLOW_THRESHOLD_MS = 50.0;
    private static final double CRITICAL_THRESHOLD_MS = 100.0;
    
    // Data storage
    private final Map<String, ProfileData> profileData = new ConcurrentHashMap<String, ProfileData>();
    private final List<SlowCall> slowCalls = Collections.synchronizedList(new ArrayList<SlowCall>());
    private static final int MAX_SLOW_CALLS = 1000;
    
    // Thread-local for tracking nested calls
    private final ThreadLocal<Deque<CallFrame>> callStack = new ThreadLocal<Deque<CallFrame>>() {
        @Override
        protected Deque<CallFrame> initialValue() {
            return new ArrayDeque<CallFrame>();
        }
    };
    
    // Tick timing
    private long lastTickTime = 0;
    private long tickCount = 0;
    private long totalTickTime = 0;
    private long maxTickTime = 0;
    private final List<Long> tickTimes = Collections.synchronizedList(new ArrayList<Long>());
    private static final int MAX_TICK_SAMPLES = 6000; // ~5 minutes at 20 TPS
    
    // Memory tracking for leak detection
    private final List<MemorySample> memorySamples = Collections.synchronizedList(new ArrayList<MemorySample>());
    private static final int MAX_MEMORY_SAMPLES = 600; // 10 minutes at 1 sample/sec
    private long lastMemorySampleTime = 0;
    private static final long MEMORY_SAMPLE_INTERVAL_MS = 1000; // Sample every second
    
    // Chunk generation tracking
    private long chunksGenerated = 0;
    private long chunksLoaded = 0;
    private long chunksUnloaded = 0;
    private double totalChunkGenTime = 0;
    private double maxChunkGenTime = 0;
    private final List<Double> chunkGenTimes = Collections.synchronizedList(new ArrayList<Double>());
    private static final int MAX_CHUNK_SAMPLES = 1000;
    
    private ServerProfiler() {}
    
    public static ServerProfiler getInstance() {
        return INSTANCE;
    }
    
    /**
     * Start profiling session.
     */
    public void start() {
        clear();
        enabled = true;
        profilingStartTime = System.currentTimeMillis();
        profilingEndTime = 0;
        log.info("[ServerProfiler] Started profiling session");
    }
    
    /**
     * Stop profiling session.
     */
    public void stop() {
        if (enabled) {
            enabled = false;
            profilingEndTime = System.currentTimeMillis();
            log.info("[ServerProfiler] Stopped profiling session. Duration: " + 
                ((profilingEndTime - profilingStartTime) / 1000.0) + " seconds");
        }
    }
    
    /**
     * Clear all profiling data.
     */
    public void clear() {
        profileData.clear();
        slowCalls.clear();
        tickTimes.clear();
        tickCount = 0;
        totalTickTime = 0;
        maxTickTime = 0;
        lastTickTime = 0;
        // Clear memory tracking
        memorySamples.clear();
        lastMemorySampleTime = 0;
        // Clear chunk tracking
        chunksGenerated = 0;
        chunksLoaded = 0;
        chunksUnloaded = 0;
        totalChunkGenTime = 0;
        maxChunkGenTime = 0;
        chunkGenTimes.clear();
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Begin timing a section of code.
     */
    public void startSection(String sectionName) {
        if (!enabled) return;
        
        Deque<CallFrame> stack = callStack.get();
        String fullPath = stack.isEmpty() ? sectionName : stack.peek().path + "/" + sectionName;
        stack.push(new CallFrame(fullPath, System.nanoTime()));
    }
    
    /**
     * End timing for the current section.
     */
    public void endSection() {
        if (!enabled) return;
        
        long endTime = System.nanoTime();
        Deque<CallFrame> stack = callStack.get();
        
        if (stack.isEmpty()) return;
        
        CallFrame frame = stack.pop();
        double durationMs = (endTime - frame.startTime) / 1_000_000.0;
        
        // Record in aggregate data
        ProfileData data = profileData.get(frame.path);
        if (data == null) {
            data = new ProfileData(frame.path);
            profileData.put(frame.path, data);
        }
        data.recordCall(durationMs);
        
        // Record slow calls
        if (durationMs >= SLOW_THRESHOLD_MS && slowCalls.size() < MAX_SLOW_CALLS) {
            SlowCall.Severity severity = SlowCall.Severity.SLOW;
            if (durationMs >= CRITICAL_THRESHOLD_MS) {
                severity = SlowCall.Severity.CRITICAL;
            } else if (durationMs >= VERY_SLOW_THRESHOLD_MS) {
                severity = SlowCall.Severity.VERY_SLOW;
            }
            slowCalls.add(new SlowCall(frame.path, durationMs, severity, System.currentTimeMillis()));
        }
    }
    
    /**
     * End one section and start another.
     */
    public void endStartSection(String sectionName) {
        endSection();
        startSection(sectionName);
    }
    
    /**
     * Record a tick time.
     */
    public void recordTickTime() {
        if (!enabled) return;
        
        long currentTime = System.nanoTime();
        if (lastTickTime > 0) {
            long tickTime = currentTime - lastTickTime;
            long tickTimeMs = tickTime / 1_000_000;
            
            tickCount++;
            totalTickTime += tickTimeMs;
            if (tickTimeMs > maxTickTime) {
                maxTickTime = tickTimeMs;
            }
            
            if (tickTimes.size() < MAX_TICK_SAMPLES) {
                tickTimes.add(tickTimeMs);
            }
        }
        lastTickTime = currentTime;
        
        // Sample memory periodically
        long now = System.currentTimeMillis();
        if (now - lastMemorySampleTime >= MEMORY_SAMPLE_INTERVAL_MS) {
            recordMemorySample();
            lastMemorySampleTime = now;
        }
    }
    
    /**
     * Record a memory sample for leak detection.
     */
    private void recordMemorySample() {
        if (!enabled) return;
        
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long total = rt.totalMemory();
        long max = rt.maxMemory();
        
        if (memorySamples.size() < MAX_MEMORY_SAMPLES) {
            memorySamples.add(new MemorySample(System.currentTimeMillis(), used, total, max));
        }
    }
    
    /**
     * Record a chunk generation event.
     * @param generationTimeMs Time taken to generate the chunk in milliseconds
     */
    public void recordChunkGenerated(double generationTimeMs) {
        if (!enabled) return;
        
        chunksGenerated++;
        totalChunkGenTime += generationTimeMs;
        if (generationTimeMs > maxChunkGenTime) {
            maxChunkGenTime = generationTimeMs;
        }
        if (chunkGenTimes.size() < MAX_CHUNK_SAMPLES) {
            chunkGenTimes.add(generationTimeMs);
        }
    }
    
    /**
     * Record a chunk load event.
     */
    public void recordChunkLoaded() {
        if (!enabled) return;
        chunksLoaded++;
    }
    
    /**
     * Record a chunk unload event.
     */
    public void recordChunkUnloaded() {
        if (!enabled) return;
        chunksUnloaded++;
    }
    
    /**
     * Analyze memory samples for potential leaks.
     */
    private MemoryAnalysis analyzeMemory() {
        if (memorySamples.size() < 10) {
            return new MemoryAnalysis(false, 0, 0, 0, "Insufficient samples");
        }
        
        List<MemorySample> samples;
        synchronized (memorySamples) {
            samples = new ArrayList<MemorySample>(memorySamples);
        }
        
        long startTime = samples.get(0).timestamp;
        long startUsed = samples.get(0).usedMemory;
        long endUsed = samples.get(samples.size() - 1).usedMemory;
        long peakUsed = 0;
        
        double sumUsed = 0;
        for (MemorySample s : samples) {
            sumUsed += s.usedMemory;
            if (s.usedMemory > peakUsed) peakUsed = s.usedMemory;
        }
        double avgUsed = sumUsed / samples.size();
        
        long duration = samples.get(samples.size() - 1).timestamp - startTime;
        double growthRate = 0;
        if (duration > 0) {
            growthRate = (endUsed - startUsed) * 1000.0 / duration;
        }
        
        boolean potentialLeak = growthRate > 10000 && (endUsed - startUsed) > 10 * 1024 * 1024;
        
        String message;
        if (potentialLeak) {
            message = "WARNING: Potential memory leak detected! Memory grew by " + 
                      ((endUsed - startUsed) / 1024 / 1024) + " MB";
        } else if (growthRate > 5000) {
            message = "Memory usage is increasing but may stabilize";
        } else if (growthRate < -5000) {
            message = "Memory usage is decreasing (GC active)";
        } else {
            message = "Memory usage is stable";
        }
        
        return new MemoryAnalysis(potentialLeak, growthRate, peakUsed, (long) avgUsed, message);
    }
    
    /**
     * Helper method to repeat a string (Java 8 compatible).
     */
    private static String repeatString(String str, int count) {
        if (count <= 0) return "";
        StringBuilder sb = new StringBuilder(str.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
    
    /**
     * Generate a profiling report.
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        sb.append(repeatString("=", 80)).append("\n");
        sb.append("MINECRAFT OLDSCHOOL EDITION SERVER - PERFORMANCE PROFILE REPORT\n");
        sb.append(repeatString("=", 80)).append("\n\n");
        
        // Session info
        sb.append("Session Information:\n");
        sb.append(repeatString("-", 40)).append("\n");
        sb.append("  Start Time: ").append(sdf.format(new Date(profilingStartTime))).append("\n");
        if (profilingEndTime > 0) {
            sb.append("  End Time: ").append(sdf.format(new Date(profilingEndTime))).append("\n");
            sb.append("  Duration: ").append(String.format("%.2f", (profilingEndTime - profilingStartTime) / 1000.0)).append(" seconds\n");
        } else {
            sb.append("  Status: Still profiling\n");
        }
        sb.append("\n");
        
        // Tick timing summary
        sb.append("Tick Timing Summary:\n");
        sb.append(repeatString("-", 40)).append("\n");
        if (tickCount > 0) {
            double avgTickTime = totalTickTime / (double) tickCount;
            double avgTps = avgTickTime > 0 ? 1000.0 / avgTickTime : 0;
            sb.append("  Total Ticks: ").append(tickCount).append("\n");
            sb.append("  Average Tick Time: ").append(String.format("%.2f", avgTickTime)).append(" ms\n");
            sb.append("  Average TPS: ").append(String.format("%.1f", avgTps)).append("\n");
            sb.append("  Max Tick Time: ").append(maxTickTime).append(" ms (").append(String.format("%.1f", maxTickTime > 0 ? 1000.0 / maxTickTime : 0)).append(" tps)\n");
            
            // Calculate percentiles
            if (!tickTimes.isEmpty()) {
                List<Long> sorted = new ArrayList<Long>(tickTimes);
                Collections.sort(sorted);
                int p99Index = (int) (sorted.size() * 0.99);
                int p95Index = (int) (sorted.size() * 0.95);
                int p50Index = sorted.size() / 2;
                sb.append("  50th Percentile: ").append(sorted.get(p50Index)).append(" ms\n");
                sb.append("  95th Percentile: ").append(sorted.get(p95Index)).append(" ms\n");
                sb.append("  99th Percentile: ").append(sorted.get(p99Index)).append(" ms\n");
            }
        } else {
            sb.append("  No tick data collected\n");
        }
        sb.append("\n");
        
        // Slowest sections (by average time)
        sb.append("Top 20 Slowest Sections (by average time):\n");
        sb.append(repeatString("-", 80)).append("\n");
        sb.append(String.format("%-50s %10s %10s %10s\n", "Section", "Avg (ms)", "Max (ms)", "Calls"));
        sb.append(repeatString("-", 80)).append("\n");
        
        List<ProfileData> sortedByAvg = new ArrayList<ProfileData>(profileData.values());
        Collections.sort(sortedByAvg, new Comparator<ProfileData>() {
            @Override
            public int compare(ProfileData a, ProfileData b) {
                return Double.compare(b.getAverageTime(), a.getAverageTime());
            }
        });
        
        int count = 0;
        for (ProfileData data : sortedByAvg) {
            if (count++ >= 20) break;
            String name = data.path;
            if (name.length() > 50) {
                name = "..." + name.substring(name.length() - 47);
            }
            sb.append(String.format("%-50s %10.2f %10.2f %10d\n", 
                name, data.getAverageTime(), data.maxTime, data.callCount));
        }
        sb.append("\n");
        
        // Sections with most total time
        sb.append("Top 20 Sections by Total Time:\n");
        sb.append(repeatString("-", 80)).append("\n");
        sb.append(String.format("%-50s %12s %8s\n", "Section", "Total (ms)", "% Time"));
        sb.append(repeatString("-", 80)).append("\n");
        
        List<ProfileData> sortedByTotal = new ArrayList<ProfileData>(profileData.values());
        Collections.sort(sortedByTotal, new Comparator<ProfileData>() {
            @Override
            public int compare(ProfileData a, ProfileData b) {
                return Double.compare(b.totalTime, a.totalTime);
            }
        });
        
        double grandTotal = 0;
        for (ProfileData d : sortedByTotal) {
            grandTotal += d.totalTime;
        }
        
        count = 0;
        for (ProfileData data : sortedByTotal) {
            if (count++ >= 20) break;
            String name = data.path;
            if (name.length() > 50) {
                name = "..." + name.substring(name.length() - 47);
            }
            double pct = grandTotal > 0 ? (data.totalTime / grandTotal) * 100 : 0;
            sb.append(String.format("%-50s %12.1f %7.1f%%\n", name, data.totalTime, pct));
        }
        sb.append("\n");
        
        // Slow call summary
        long criticalCount = 0, verySlowCount = 0, slowOnlyCount = 0;
        synchronized (slowCalls) {
            for (SlowCall c : slowCalls) {
                if (c.severity == SlowCall.Severity.CRITICAL) criticalCount++;
                else if (c.severity == SlowCall.Severity.VERY_SLOW) verySlowCount++;
                else slowOnlyCount++;
            }
        }
        
        sb.append("Slow Call Summary:\n");
        sb.append(repeatString("-", 40)).append("\n");
        sb.append("  Critical (>").append((int)CRITICAL_THRESHOLD_MS).append("ms): ").append(criticalCount).append("\n");
        sb.append("  Very Slow (>").append((int)VERY_SLOW_THRESHOLD_MS).append("ms): ").append(verySlowCount).append("\n");
        sb.append("  Slow (>").append((int)SLOW_THRESHOLD_MS).append("ms): ").append(slowOnlyCount).append("\n");
        sb.append("\n");
        
        // Memory Analysis (Leak Detection)
        sb.append("Memory Analysis (Leak Detection):\n");
        sb.append(repeatString("-", 80)).append("\n");
        MemoryAnalysis memAnalysis = analyzeMemory();
        if (memorySamples.size() >= 10) {
            sb.append("  Samples Collected: ").append(memorySamples.size()).append("\n");
            sb.append("  Peak Memory Used: ").append(memAnalysis.peakMemory / 1024 / 1024).append(" MB\n");
            sb.append("  Average Memory Used: ").append(memAnalysis.avgMemory / 1024 / 1024).append(" MB\n");
            sb.append("  Memory Growth Rate: ").append(String.format("%.2f", memAnalysis.growthRateBytesPerSec / 1024)).append(" KB/sec\n");
            if (memAnalysis.potentialLeak) {
                sb.append("  *** ").append(memAnalysis.message).append(" ***\n");
            } else {
                sb.append("  Status: ").append(memAnalysis.message).append("\n");
            }
            
            // Show memory timeline
            if (!memorySamples.isEmpty()) {
                sb.append("\n  Memory Timeline (first -> last):\n");
                int step = Math.max(1, memorySamples.size() / 10);
                for (int i = 0; i < memorySamples.size(); i += step) {
                    MemorySample s = memorySamples.get(i);
                    long usedMB = s.usedMemory / 1024 / 1024;
                    long totalMB = s.totalMemory / 1024 / 1024;
                    sb.append("    [").append(i).append("] ").append(usedMB).append("/").append(totalMB).append(" MB\n");
                }
            }
        } else {
            sb.append("  Insufficient memory samples (need at least 10 seconds of data)\n");
        }
        sb.append("\n");
        
        // Chunk Generation Analysis
        sb.append("Chunk Generation Analysis:\n");
        sb.append(repeatString("-", 80)).append("\n");
        if (chunksGenerated > 0 || chunksLoaded > 0 || chunksUnloaded > 0) {
            sb.append("  Chunks Generated: ").append(chunksGenerated).append("\n");
            sb.append("  Chunks Loaded: ").append(chunksLoaded).append("\n");
            sb.append("  Chunks Unloaded: ").append(chunksUnloaded).append("\n");
            if (chunksGenerated > 0) {
                double avgGenTime = totalChunkGenTime / chunksGenerated;
                sb.append("  Average Generation Time: ").append(String.format("%.2f", avgGenTime)).append(" ms\n");
                sb.append("  Max Generation Time: ").append(String.format("%.2f", maxChunkGenTime)).append(" ms\n");
                
                if (chunkGenTimes.size() >= 10) {
                    List<Double> sorted = new ArrayList<Double>(chunkGenTimes);
                    Collections.sort(sorted);
                    int p95Index = (int) (sorted.size() * 0.95);
                    int p99Index = (int) (sorted.size() * 0.99);
                    sb.append("  95th Percentile: ").append(String.format("%.2f", sorted.get(p95Index))).append(" ms\n");
                    sb.append("  99th Percentile: ").append(String.format("%.2f", sorted.get(p99Index))).append(" ms\n");
                }
                
                double avgGenTimeMs = totalChunkGenTime / chunksGenerated;
                if (avgGenTimeMs > 50) {
                    sb.append("  *** WARNING: Chunk generation is slow (>50ms avg) ***\n");
                } else if (avgGenTimeMs > 20) {
                    sb.append("  NOTICE: Chunk generation is moderate (>20ms avg)\n");
                }
            }
        } else {
            sb.append("  No chunk generation data collected\n");
            sb.append("  (Chunk tracking hooks may not be installed)\n");
        }
        sb.append("\n");
        
        // System info
        sb.append("System Information:\n");
        sb.append(repeatString("-", 40)).append("\n");
        Runtime rt = Runtime.getRuntime();
        sb.append("  Java Version: ").append(System.getProperty("java.version")).append("\n");
        sb.append("  OS: ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version")).append("\n");
        sb.append("  Available Processors: ").append(rt.availableProcessors()).append("\n");
        sb.append("  Max Memory: ").append(rt.maxMemory() / 1024 / 1024).append(" MB\n");
        sb.append("  Total Memory: ").append(rt.totalMemory() / 1024 / 1024).append(" MB\n");
        sb.append("  Free Memory: ").append(rt.freeMemory() / 1024 / 1024).append(" MB\n");
        sb.append("  Used Memory: ").append((rt.totalMemory() - rt.freeMemory()) / 1024 / 1024).append(" MB\n");
        sb.append("\n");
        
        sb.append(repeatString("=", 80)).append("\n");
        sb.append("END OF REPORT\n");
        sb.append(repeatString("=", 80)).append("\n");
        
        return sb.toString();
    }
    
    /**
     * Save the report to a file.
     */
    public String saveReport() {
        try {
            File profilesDir = new File("profiles");
            if (!profilesDir.exists()) {
                profilesDir.mkdirs();
            }
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String filename = "server_profile_" + sdf.format(new Date()) + ".txt";
            File reportFile = new File(profilesDir, filename);
            
            PrintWriter pw = new PrintWriter(new FileWriter(reportFile));
            pw.print(generateReport());
            pw.close();
            
            log.info("[ServerProfiler] Report saved to: " + reportFile.getAbsolutePath());
            return reportFile.getAbsolutePath();
        } catch (Exception e) {
            log.warning("[ServerProfiler] Failed to save report: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Get a brief status summary.
     */
    public String getStatusSummary() {
        if (!enabled && profilingStartTime == 0) {
            return "Profiler not active. Use /profile start to begin.";
        }
        
        StringBuilder sb = new StringBuilder();
        if (enabled) {
            long duration = (System.currentTimeMillis() - profilingStartTime) / 1000;
            sb.append("Profiler active for ").append(duration).append("s");
        } else {
            sb.append("Profiler stopped");
        }
        
        sb.append(" | Sections: ").append(profileData.size());
        sb.append(" | Slow: ").append(slowCalls.size());
        
        if (tickCount > 0) {
            double avgTps = totalTickTime > 0 ? (tickCount * 1000.0) / totalTickTime : 0;
            sb.append(" | TPS: ").append(String.format("%.1f", avgTps));
        }
        
        // Add memory info
        if (!memorySamples.isEmpty()) {
            Runtime rt = Runtime.getRuntime();
            long usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
            sb.append(" | Mem: ").append(usedMB).append("MB");
        }
        
        // Add chunk info
        if (chunksGenerated > 0 || chunksLoaded > 0) {
            sb.append(" | Chunks: ").append(chunksGenerated).append("g/").append(chunksLoaded).append("l");
        }
        
        return sb.toString();
    }
    
    // Inner classes for data storage
    
    private static class CallFrame {
        final String path;
        final long startTime;
        
        CallFrame(String path, long startTime) {
            this.path = path;
            this.startTime = startTime;
        }
    }
    
    private static class ProfileData {
        final String path;
        long callCount = 0;
        double totalTime = 0;
        double maxTime = 0;
        double minTime = Double.MAX_VALUE;
        
        ProfileData(String path) {
            this.path = path;
        }
        
        synchronized void recordCall(double durationMs) {
            callCount++;
            totalTime += durationMs;
            if (durationMs > maxTime) maxTime = durationMs;
            if (durationMs < minTime) minTime = durationMs;
        }
        
        double getAverageTime() {
            return callCount > 0 ? totalTime / callCount : 0;
        }
    }
    
    private static class SlowCall {
        enum Severity { SLOW, VERY_SLOW, CRITICAL }
        
        final String path;
        final double durationMs;
        final Severity severity;
        final long timestamp;
        
        SlowCall(String path, double durationMs, Severity severity, long timestamp) {
            this.path = path;
            this.durationMs = durationMs;
            this.severity = severity;
            this.timestamp = timestamp;
        }
    }
    
    private static class MemorySample {
        final long timestamp;
        final long usedMemory;
        final long totalMemory;
        final long maxMemory;
        
        MemorySample(long timestamp, long usedMemory, long totalMemory, long maxMemory) {
            this.timestamp = timestamp;
            this.usedMemory = usedMemory;
            this.totalMemory = totalMemory;
            this.maxMemory = maxMemory;
        }
    }
    
    private static class MemoryAnalysis {
        final boolean potentialLeak;
        final double growthRateBytesPerSec;
        final long peakMemory;
        final long avgMemory;
        final String message;
        
        MemoryAnalysis(boolean potentialLeak, double growthRateBytesPerSec, long peakMemory, long avgMemory, String message) {
            this.potentialLeak = potentialLeak;
            this.growthRateBytesPerSec = growthRateBytesPerSec;
            this.peakMemory = peakMemory;
            this.avgMemory = avgMemory;
            this.message = message;
        }
    }
}
