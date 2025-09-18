package com.app;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

public class AppStatus {

    private static JLabel statusLabel;
    private static volatile boolean processando = false;

    private static final String CSV_URL = "http://127.0.0.1:5000/api/csv";
    private static final String FORM_URL = "https://apirest01.pythonanywhere.com/api/documentos-impostos/";

    public static void main(String[] args) {

        // ---- Interface Swing ----
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("App Status");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(220, 100);
            frame.setResizable(false);

            JPanel panel = new JPanel();
            panel.setBackground(Color.BLACK);
            frame.add(panel);

            statusLabel = new JLabel("OFFLINE");
            statusLabel.setForeground(Color.WHITE);
            statusLabel.setFont(new Font("Arial", Font.BOLD, 16));
            panel.add(statusLabel);

            frame.setVisible(true);
            enviarStatusFlask("ONLINE");
        });

        // ---- Heartbeat ----
        new Thread(() -> {
            while (true) {
                try {
                    if (processando) enviarStatusFlask("TRABALHANDO");
                    else enviarStatusFlask("ONLINE");
                    Thread.sleep(2000); // a cada 2 segundos
                } catch (Exception e) {
                    e.printStackTrace();
                    atualizarLabel("OFFLINE");
                    try { Thread.sleep(2000); } catch (InterruptedException ex) {}
                }
            }
        }).start();

        // ---- Loop principal para processar CSV ----
        new Thread(() -> {
            while (true) {
                try {
                    File csvFile = baixarCSV();
                    if (csvFile != null) {
                        processando = true;
                        atualizarLabel("TRABALHANDO");
                        executarAutomacao(csvFile);
                        csvFile.delete();
                        processando = false;
                        atualizarLabel("ONLINE");
                        removerCSVProcessado();
                    }
                    Thread.sleep(2000); // verifica a cada 2 segundos
                } catch (Exception e) {
                    e.printStackTrace();
                    atualizarLabel("OFFLINE");
                    try { Thread.sleep(2000); } catch (InterruptedException ex) {}
                }
            }
        }).start();
    }

    private static void atualizarLabel(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }

    private static void enviarStatusFlask(String status) {
        try {
            URL url = new URL("http://127.0.0.1:5000/api/update_status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setDoOutput(true);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String jsonInput = String.format("{\"status\":\"%s\",\"ultima_execucao\":\"%s\"}", status, timestamp);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonInput.getBytes("utf-8"));
            }
            conn.getResponseCode(); // apenas envia
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static File baixarCSV() {
        try {
            URL url = new URL(CSV_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) return null;

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            File tempFile = new File("csv_para_processar.csv");
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));
            String line;
            while ((line = in.readLine()) != null) {
                writer.write(line);
                writer.newLine();
            }
            writer.close();
            in.close();
            return tempFile;
        } catch (Exception e) {
            return null;
        }
    }

    private static void removerCSVProcessado() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:5000/api/csv_processed").openConnection();
            conn.setRequestMethod("POST");
            conn.getResponseCode();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void executarAutomacao(File csvFile) {
        try {
            List<String> linhas = Files.readAllLines(csvFile.toPath());
            if (linhas.isEmpty()) return;

            WebDriver driver = new ChromeDriver();
            driver.get(FORM_URL);
            driver.manage().window().maximize();
            Thread.sleep(2000);

            for (String linha : linhas) {
                String[] dados = linha.split(",");
                String valorBase = dados[0];
                String aliquota = dados[1];
                String valorImposto = dados[2];

                driver.findElement(By.xpath("//*[@id='post-object-form']/form/fieldset/div[1]/div/input"))
                        .clear();
                driver.findElement(By.xpath("//*[@id='post-object-form']/form/fieldset/div[1]/div/input"))
                        .sendKeys(valorBase);
                Thread.sleep(3000);

                driver.findElement(By.xpath("//*[@id='post-object-form']/form/fieldset/div[2]/div/input"))
                        .clear();
                driver.findElement(By.xpath("//*[@id='post-object-form']/form/fieldset/div[2]/div/input"))
                        .sendKeys(aliquota);
                Thread.sleep(3000);

                driver.findElement(By.xpath("//*[@id='post-object-form']/form/fieldset/div[3]/div/input"))
                        .clear();
                driver.findElement(By.xpath("//*[@id='post-object-form']/form/fieldset/div[3]/div/input"))
                        .sendKeys(valorImposto);
                Thread.sleep(3000);

                driver.findElement(By.xpath("//*[@id='post-object-form']/form/fieldset/div[4]/div/select/option[4]")).click();
                driver.findElement(By.xpath("//*[@id='post-object-form']/form/fieldset/div[5]/div/select/option[18]")).click();
                Thread.sleep(3000);
                driver.findElement(By.xpath("//*[@id='post-object-form']/form/fieldset/div[6]/button")).click();
                Thread.sleep(3000); // espera entre linhas
            }

            Thread.sleep(2000);
            driver.quit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
