package com.unipi.dynamicplugins.pluginreportgenerator;

import com.unipi.dynamicplugins.pluginapi.Plugin;
import com.unipi.dynamicplugins.pluginapi.PluginAction;
import com.unipi.dynamicplugins.pluginapi.PluginContext;

import java.sql.*;
import java.util.List;

/**
 * Reports on plugins; also demonstrates cross-plugin calls via context.
 */
public class ReportGeneratorPlugin implements Plugin {

    private PluginContext context;

    @Override
    public void setContext(PluginContext context) {
        this.context = context;
    }

    private final String DB_URL = "jdbc:mysql://localhost:3306/plugin_db";
    private final String USER = "root";
    private final String PASSWORD = "";

    @Override
    public String getName() { return "Report Generator Plugin"; }

    @Override
    public String getDescription() { return "Generates a report of active plugins."; }

    /**
     * Classic usage, for plain string execution.
     */
    @Override
    public String execute(String input) {
        // You can pass input as a heading, or ignore it.
        return pluginSummaryReport();
    }

    /**
     * Public method: generates a summary (can be invoked via reflection)
     */
    @PluginAction(description = "Generates a summary report of plugins")
    public String pluginSummaryReport() {
        StringBuilder report = new StringBuilder("Plugins Report:\n");
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name, active FROM plugins")) {
            int total = 0, active = 0;
            while (rs.next()) {
                total++;
                String name = rs.getString("name");
                boolean isActive = rs.getBoolean("active");
                if (isActive) active++;
                report.append(" - ").append(name)
                        .append(isActive ? " (Active)" : " (Inactive)").append("\n");
            }
            report.append("Total plugins: ").append(total).append(", Active: ").append(active);
        } catch (Exception e) {
            report.append("Error: ").append(e.getMessage());
        }
        return report.toString();
    }

    @PluginAction(description = "Returns the count of active plugins")
    public int countActivePlugins() {
        return runCount("SELECT COUNT(*) FROM plugins WHERE active = 1");
    }

    @PluginAction(description = "Returns the count of total plugins")
    public int countTotalPlugins() {
        return runCount("SELECT COUNT(*) FROM plugins");
    }

    private int runCount(String sql) {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            return -1;
        }
        return 0;
    }

    /**
     * Example of a cross-plugin invocation via PluginContext.
     */
    @PluginAction(description = "Converts report title using another plugin")
    public String formatHeading(String heading) {
        try {
            Long stringProcessorId = context.getPluginIdByName("String Processor Plugin"); // match your actual plugin name
            if (stringProcessorId == null) {
                return "Error: String Processor Plugin not found";
            }
            Object result = context.callPluginMethod(stringProcessorId, "upper", List.of(heading));
            return "[[ " + result + " ]]";
        } catch (Exception e) {
            return "Error calling plugin: " + e.getMessage();
        }
    }
}
