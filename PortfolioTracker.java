

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;


class Stock {
    private String symbol;
    private double quantity;
    private double buyPrice;
    private double currentPrice;

    public Stock(String symbol, double quantity, double buyPrice) {
        this.symbol = symbol.toUpperCase();
        this.quantity = quantity;
        this.buyPrice = buyPrice;
        this.currentPrice = buyPrice; // default to buy price
    }

    public Stock(String symbol, double quantity, double buyPrice, double currentPrice) {
        this.symbol = symbol.toUpperCase();
        this.quantity = quantity;
        this.buyPrice = buyPrice;
        this.currentPrice = currentPrice;
    }

    public String getSymbol() { return symbol; }
    public double getQuantity() { return quantity; }
    public double getBuyPrice() { return buyPrice; }
    public double getCurrentPrice() { return currentPrice; }

    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double marketValue() { return currentPrice * quantity; }
    public double investedAmount() { return buyPrice * quantity; }
    public double profitLoss() { return marketValue() - investedAmount(); }

    public String serialize() {
        return symbol + "|" + quantity + "|" + buyPrice + "|" + currentPrice;
    }

    public static Stock deserialize(String line) {
        String[] parts = line.split("\\|");
        if (parts.length < 4) return null;
        try {
            return new Stock(parts[0], Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
        } catch (Exception e) {
            return null;
        }
    }
}

class Portfolio {
    private List<Stock> stocks = new ArrayList<>();

    public synchronized void addStock(Stock s) { stocks.add(s); }
    public synchronized List<Stock> getStocks() { return new ArrayList<>(stocks); }

    public synchronized Stock findBySymbol(String symbol) {
        for (Stock s : stocks)
            if (s.getSymbol().equalsIgnoreCase(symbol))
                return s;
        return null;
    }

    public synchronized boolean removeBySymbol(String symbol) {
        Iterator<Stock> it = stocks.iterator();
        while (it.hasNext()) {
            if (it.next().getSymbol().equalsIgnoreCase(symbol)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    public synchronized void saveToFile(File f) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f))) {
            for (Stock s : stocks) {
                bw.write(s.serialize());
                bw.newLine();
            }
        }
    }

    public synchronized void loadFromFile(File f) throws IOException {
        stocks.clear();
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                Stock s = Stock.deserialize(line);
                if (s != null) stocks.add(s);
            }
        }
    }

    public synchronized void exportToCSV(File f) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f))) {
            bw.write("Symbol,Quantity,BuyPrice,CurrentPrice,MarketValue,Invested,ProfitLoss");
            bw.newLine();
            for (Stock s : stocks) {
                bw.write(String.format(Locale.US, "%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f",
                        s.getSymbol(), s.getQuantity(), s.getBuyPrice(), s.getCurrentPrice(),
                        s.marketValue(), s.investedAmount(), s.profitLoss()));
                bw.newLine();
            }
        }
    }
}

public class PortfolioTracker {
    private JFrame frame;
    private JTable table;
    private DefaultTableModel model;
    private Portfolio portfolio = new Portfolio();
    private final File dataFile = new File("portfolio.txt");

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PortfolioTracker().createAndShowGUI());
    }

    private void createAndShowGUI() {
        frame = new JFrame("Stock Portfolio Tracker");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 500);
        frame.setLocationRelativeTo(null);

        model = new DefaultTableModel(new Object[]{"Symbol", "Qty", "Buy Price", "Cur Price", "Market Value", "Invested", "P/L"}, 0) {
            public boolean isCellEditable(int row, int column) { return false; }
        };

        table = new JTable(model);
        JScrollPane scrollPane = new JScrollPane(table);

        JPanel btnPanel = new JPanel();
        JButton addBtn = new JButton("Add Stock");
        JButton updateBtn = new JButton("Update Price");
        JButton removeBtn = new JButton("Remove Stock");
        JButton saveBtn = new JButton("Save");
        JButton loadBtn = new JButton("Load");
        JButton exportBtn = new JButton("Export CSV");

        btnPanel.add(addBtn);
        btnPanel.add(updateBtn);
        btnPanel.add(removeBtn);
        btnPanel.add(saveBtn);
        btnPanel.add(loadBtn);
        btnPanel.add(exportBtn);

        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(btnPanel, BorderLayout.SOUTH);

        addBtn.addActionListener(e -> onAddStock());
        updateBtn.addActionListener(e -> onUpdatePrice());
        removeBtn.addActionListener(e -> onRemoveStock());
        saveBtn.addActionListener(e -> onSave());
        loadBtn.addActionListener(e -> onLoad());
        exportBtn.addActionListener(e -> onExport());

        try {
            portfolio.loadFromFile(dataFile);
            refreshTable();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Load failed: " + ex.getMessage());
        }

        frame.setVisible(true);
    }

    private void refreshTable() {
        model.setRowCount(0);
        for (Stock s : portfolio.getStocks()) {
            model.addRow(new Object[]{
                    s.getSymbol(),
                    String.format(Locale.US, "%.4f", s.getQuantity()),
                    String.format(Locale.US, "%.4f", s.getBuyPrice()),
                    String.format(Locale.US, "%.4f", s.getCurrentPrice()),
                    String.format(Locale.US, "%.4f", s.marketValue()),
                    String.format(Locale.US, "%.4f", s.investedAmount()),
                    String.format(Locale.US, "%.4f", s.profitLoss())
            });
        }
    }

    private void onAddStock() {
        JTextField symbolF = new JTextField();
        JTextField qtyF = new JTextField();
        JTextField buyF = new JTextField();
        Object[] msg = {"Symbol:", symbolF, "Quantity:", qtyF, "Buy Price:", buyF};
        int opt = JOptionPane.showConfirmDialog(frame, msg, "Add Stock", JOptionPane.OK_CANCEL_OPTION);
        if (opt != JOptionPane.OK_OPTION) return;

        try {
            String sym = symbolF.getText().trim().toUpperCase();
            double qty = Double.parseDouble(qtyF.getText().trim());
            double buy = Double.parseDouble(buyF.getText().trim());
            portfolio.addStock(new Stock(sym, qty, buy));
            refreshTable();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Invalid input.");
        }
    }

    private void onUpdatePrice() {
        String sym = JOptionPane.showInputDialog(frame, "Enter stock symbol:");
        if (sym == null || sym.trim().isEmpty()) return;
        Stock s = portfolio.findBySymbol(sym.trim());
        if (s == null) {
            JOptionPane.showMessageDialog(frame, "Stock not found.");
            return;
        }
        String priceStr = JOptionPane.showInputDialog(frame, "Enter new price:", s.getCurrentPrice());
        try {
            double price = Double.parseDouble(priceStr);
            s.setCurrentPrice(price);
            refreshTable();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Invalid price.");
        }
    }

    private void onRemoveStock() {
        String sym = JOptionPane.showInputDialog(frame, "Enter stock symbol to remove:");
        if (sym == null || sym.trim().isEmpty()) return;
        if (portfolio.removeBySymbol(sym.trim())) refreshTable();
        else JOptionPane.showMessageDialog(frame, "Stock not found.");
    }

    private void onSave() {
        try {
            portfolio.saveToFile(dataFile);
            JOptionPane.showMessageDialog(frame, "Saved to " + dataFile.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Save failed: " + ex.getMessage());
        }
    }

    private void onLoad() {
        try {
            portfolio.loadFromFile(dataFile);
            refreshTable();
            JOptionPane.showMessageDialog(frame, "Loaded from " + dataFile.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Load failed: " + ex.getMessage());
        }
    }

    private void onExport() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("portfolio_export.csv"));
        int ret = chooser.showSaveDialog(frame);
        if (ret != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        try {
            portfolio.exportToCSV(f);
            JOptionPane.showMessageDialog(frame, "Exported to " + f.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Export failed: " + ex.getMessage());
        }
    }
}
