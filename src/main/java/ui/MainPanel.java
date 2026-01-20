

package ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import model.User;
import dao.TradeDao;
import dao.PriceHistoryDao;
import dao.PortfolioValueDao;
import util.PriceHistorySeeder;
import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainPanel extends JPanel {

    private User user;

    private JTable portfolioTable;
    private DefaultTableModel portfolioModel;

    private JPanel chartPanel;
    private JPanel watchListContent;
    private DonutChartPanel donutChartPanel;
    private PortfolioLineChartPanel lineChartPanel;

    // üst metrik kartları için label referansları
    private JLabel lblPortfolioValue;
    private JLabel lblTotalPl;

    // Kar/Zarar gösterim modu: true = yüzde, false = miktar
    private boolean showPlAsPercentage = false;
    
    // Adet/Tutar gösterim modu: true = Adet, false = Tutar
    private boolean showQuantityInsteadOfAmount = true;
    
    // Portföy değeri görünürlük durumu: true = görünür, false = gizli
    private boolean portfolioValueVisible = true;
    
    // Tema toggle butonu referansı
    private JButton themeToggleButton;
    
    // Otomatik fiyat güncelleme timer'ı (1 dakika)
    private javax.swing.Timer autoRefreshTimer;

    private static final Font FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    
    // Renk getter metodları (ThemeManager'dan alır)
    private Color getBG() { return ThemeManager.getBackground(); }
    private Color getPANEL_BG() { return ThemeManager.getPanelBackground(); }
    private Color getBORDER() { return ThemeManager.getBorder(); }
    private Color getTEXT() { return ThemeManager.getText(); }
    private Color getTEXT_MUTED() { return ThemeManager.getTextMuted(); }
    private Color getTABLE_HEADER() { return ThemeManager.getTableHeader(); }
    private Color getTABLE_ROW_ALT() { return ThemeManager.getTableRowAlt(); }
    private static final DecimalFormat PRICE_FMT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat PCT_FMT = new DecimalFormat("#,##0.00'%'");
    // Adet için format: gereksiz sıfırları göstermez, yeterli hassasiyet sağlar (örn: 0.0005, 3.5, 10)
    private static final DecimalFormat QTY_FMT = new DecimalFormat("#,##0.########");
    // Sabit kur: 1 USD = 43 TL
    private static final double USD_TO_TRY = 43.0;

    public MainPanel(User user) {
        this.user = user;
        
        // FlatLaf tema başlat
        initializeTheme();

        initUI();
    }
    
    /**
     * Kullanıcıyı değiştirir (logout/login senaryosu için)
     */
    public void setUser(User user) {
        this.user = user;
        // Kullanıcı değiştiğinde UI'ı yeniden yükle
        loadPortfolioFromDb();
    }
    
    private void initializeTheme() {
        try {
            if (ThemeManager.isDarkTheme()) {
                UIManager.setLookAndFeel(new FlatMacDarkLaf());
            } else {
                UIManager.setLookAndFeel(new FlatMacLightLaf());
            }
            // JPanel için de updateComponentTreeUI çalışır
            SwingUtilities.updateComponentTreeUI(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void initUI() {
        setLayout(new BorderLayout(12, 12));
        setBorder(new EmptyBorder(12, 12, 12, 12));
        setBackground(getBG());

        JPanel header = buildHeader();
        JPanel body = buildBody();

        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);

        loadPortfolioFromDb();
        
        // Otomatik fiyat güncelleme timer'ını başlat (1 dakika = 60000 ms)
        startAutoRefreshTimer();
    }
    
    /**
     * Parent JFrame'i bulur (dialog'lar için)
     */
    private JFrame getParentFrame() {
        Component parent = this;
        while (parent != null && !(parent instanceof JFrame)) {
            parent = parent.getParent();
        }
        return (JFrame) parent;
    }
    private void loadPortfolioFromDb() {
        // Eğer tablo üzerinde bir hücre editleme modundaysa önce durdur
        if (portfolioTable != null && portfolioTable.isEditing()) {
            TableCellEditor editor = portfolioTable.getCellEditor();
            if (editor != null) {
                editor.stopCellEditing();
            }
        }

        portfolioModel.setRowCount(0);

        dao.PortfolioDao dao = new dao.PortfolioDao();
        var rows = dao.getPortfolioSummary(user.getId());
        PriceHistoryDao priceHistoryDao = new PriceHistoryDao();

        double totalValue = 0.0; // TL karşılığı
        double totalCost = 0.0;  // TL karşılığı

        for (var r : rows) {
            // 1) prices_history tablosundan son fiyatı dene
            Double lastPrice = priceHistoryDao.findLatestPrice(r.assetId);
            double currentPrice = (lastPrice != null) ? lastPrice : r.avgCost;

            // 2) Piyasa türüne göre USD pozisyonları TL'ye çevir (US, CRYPTO ve COMMODITY USD cinsinden)
            String market = r.market != null ? r.market.toUpperCase() : "";
            double fx = ("US".equals(market) || "CRYPTO".equals(market) || "COMMODITY".equals(market)) ? USD_TO_TRY : 1.0;

            double valueTl = currentPrice * fx * r.quantity;
            double costTl = r.avgCost * fx * r.quantity;
            double plTl = valueTl - costTl;

            totalValue += valueTl;
            totalCost += costTl;

            portfolioModel.addRow(new Object[]{
                    new String[]{r.symbol, r.name != null ? r.name : ""}, // Varlık: [sembol, isim] - index 0
                    r.market,                                              // Piyasa - index 1
                    r.quantity,     // Adet - index 2 (görüntüleme için)
                    valueTl,        // Tutar (TL cinsinden) - index 3 (gizli, görüntüleme için)
                    r.avgCost,      // Ort. Maliyet - index 4
                    currentPrice,   // Güncel fiyat - index 5
                    plTl,           // Kar/Zarar - index 6
                    "Sil",          // Sil butonu - index 7
                    r.assetId       // ASSET_ID - index 8 (gizli)
            });
        }
        
        // Portföy toplam değerini veritabanına kaydet
        PortfolioValueDao portfolioValueDao = new PortfolioValueDao();
        portfolioValueDao.insert(user.getId(), totalValue);
        
        updateMetrics(totalValue, totalValue - totalCost);
        refreshWatchlist();
        adjustTableHeight(); // Tablo yüksekliğini satır sayısına göre ayarla
        updateDonutChart(rows, priceHistoryDao); // Halka grafiği güncelle
        updateLineChart(); // Line chart'ı güncelle
    }
    private JPanel buildHeader() {
        JPanel container = new JPanel();
        container.setOpaque(false);
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        JLabel welcome = new JLabel("Hoş geldiniz, " + user.getFullName());
        welcome.setFont(new Font("Segoe UI", Font.BOLD, 18));
        welcome.setForeground(getTEXT());

        JLabel subtitle = new JLabel("Portföy Terminali");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(getTEXT_MUTED());

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(welcome);
        left.add(subtitle);

        // Tema toggle butonu (sağ üst köşe)
        themeToggleButton = createThemeToggleButton();
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(themeToggleButton);

        topBar.add(left, BorderLayout.WEST);
        topBar.add(rightPanel, BorderLayout.EAST);

        JPanel metrics = new JPanel(new GridLayout(1, 2, 12, 0));
        metrics.setOpaque(false);
        metrics.setBorder(new EmptyBorder(10, 0, 0, 0));
        metrics.add(createMetricCard("Portföy Değeri", "₺0,00", "Toplam piyasa değeri", new Color(25, 135, 84))); // Koyu yeşil
        metrics.add(createMetricCard("Toplam P/L", "+₺0,00", "Gerçekleşmemiş kar/zarar", new Color(25, 135, 84))); // Koyu yeşil

        container.add(topBar);
        container.add(metrics);


        return container;
    }
    private JPanel buildBody() {
        JPanel body = new JPanel(new BorderLayout(12, 12));
        body.setOpaque(false);

        JPanel tableOnly = new JPanel(new BorderLayout(12, 0));
        tableOnly.setOpaque(false);
        tableOnly.add(buildPortfolioSection(), BorderLayout.CENTER);

        body.add(tableOnly, BorderLayout.CENTER);
        body.add(buildChartSection(), BorderLayout.SOUTH);

        return body;
    }

    private JPanel buildPortfolioSection() {
        portfolioModel = new DefaultTableModel(
                new Object[]{"Varlık", "Piyasa Türü ", "Adet/Tutar", "Tutar (Gizli)", "Ort. Maliyet", "Güncel Fiyat", "Kar/Zarar", "", "ASSET_ID"},
                0
        ) {
            @Override public boolean isCellEditable(int row, int column) {
                // Sadece \"Sil\" sütunu (index 6) tıklanabilir olsun
                return column == 6;
            }
        };
        portfolioTable = new JTable(portfolioModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                c.setFont(FONT_REGULAR);
                
                // Hücrelerin iç boşluğu ve odak çerçevesi
                if (c instanceof JComponent jc) {
                    // Tüm hücreler için dengeli yatay padding
                    jc.setBorder(new EmptyBorder(0, 8, 0, 8));
                }
                
                if (!isRowSelected(row)) {
                    c.setBackground((row % 2 == 0) ? getPANEL_BG() : getTABLE_ROW_ALT());
                    c.setForeground(getTEXT());
                } else {
                    // Seçili satır için tema değişimine göre renkleri ayarla
                    if (ThemeManager.isDarkTheme()) {
                        c.setBackground(new Color(45, 45, 45)); // #2d2d2d
                        c.setForeground(Color.WHITE);
                    } else {
                        c.setBackground(new Color(227, 242, 253)); // #E3F2FD
                        c.setForeground(Color.BLACK);
                    }
                }
                if (column == 2) { // Adet/Tutar sütunu (görüntüleme kolonu)
                    if (showQuantityInsteadOfAmount) {
                        // Adet göster - model'de index 2'de Adet var
                        Object qtyObj = portfolioModel.getValueAt(row, 2);
                        if (qtyObj instanceof Number) {
                            double qty = ((Number) qtyObj).doubleValue();
                            ((JLabel) c).setText(QTY_FMT.format(qty));
                        }
                    } else {
                        // Tutar göster (TL cinsinden) - model'de index 3'te Tutar var
                        Object tutarObj = portfolioModel.getValueAt(row, 3);
                        if (tutarObj instanceof Number) {
                            double tutar = ((Number) tutarObj).doubleValue();
                            ((JLabel) c).setText("₺" + PRICE_FMT.format(tutar));
                        }
                    }
                }
                if (column == 3) { // Ort. Maliyet sütunu
                    Object value = getValueAt(row, column);
                    if (value instanceof Number) {
                        double avgCost = ((Number) value).doubleValue();
                        // Piyasa türüne göre para birimi ekle
                        Object marketObj = getValueAt(row, 1); // Piyasa sütunu
                        String marketStr = marketObj != null ? marketObj.toString().toUpperCase() : "";
                        String currencySymbol = ("US".equals(marketStr) || "CRYPTO".equals(marketStr)) ? "$" : "₺";
                        ((JLabel) c).setText(PRICE_FMT.format(avgCost) + currencySymbol);
                    }
                }
                if (column == 4) { // Güncel Fiyat sütunu
                    Object value = getValueAt(row, column);
                    if (value instanceof Number) {
                        double price = ((Number) value).doubleValue();
                        // Piyasa türüne göre para birimi ekle
                        Object marketObj = getValueAt(row, 1); // Piyasa sütunu
                        String marketStr = marketObj != null ? marketObj.toString().toUpperCase() : "";
                        String currencySymbol = ("US".equals(marketStr) || "CRYPTO".equals(marketStr)) ? "$" : "₺";
                        ((JLabel) c).setText(PRICE_FMT.format(price) + currencySymbol);
                    }
                }
                if (column == 5) { // Kar/Zarar sütunu
                    double plTl = Double.parseDouble(getValueAt(row, column).toString());

                    // Yüzde veya miktar gösterimi
                    String displayText;
                    if (showPlAsPercentage) {
                        // Yüzde hesapla: (plTl / costTl) * 100
                        Object qtyObj = portfolioModel.getValueAt(row, 2); // Adet
                        Object avgCostObj = portfolioModel.getValueAt(row, 4); // Ort. Maliyet
                        Object marketObj = getValueAt(row, 1); // Piyasa

                        double qty = qtyObj instanceof Number ? ((Number) qtyObj).doubleValue() : 0;
                        double avgCost = avgCostObj instanceof Number ? ((Number) avgCostObj).doubleValue() : 0;
                        String marketStr = marketObj != null ? marketObj.toString().toUpperCase() : "";
                        double fx = ("US".equals(marketStr) || "CRYPTO".equals(marketStr) || "COMMODITY".equals(marketStr)) ? USD_TO_TRY : 1.0;
                        double costTl = avgCost * fx * qty;

                        double percentage = (costTl != 0) ? (plTl / costTl) * 100.0 : 0.0;
                        displayText = (percentage >= 0 ? "+" : "") + PCT_FMT.format(percentage);
                    } else {
                        // Miktar gösterimi (2 ondalık basamak)
                        DecimalFormat plFmt = new DecimalFormat("#,##0.00");
                        displayText = (plTl >= 0 ? "+₺" : "-₺") + plFmt.format(Math.abs(plTl));
                    }

                    ((JLabel) c).setText(displayText);
                    
                    // Kar/zarar renkleri - seçili satırda okunabilirlik için kontrast kontrolü
                    boolean isSelected = isRowSelected(row);
                    if (isSelected && !ThemeManager.isDarkTheme()) {
                        // Açık temada seçili satır: daha koyu renkler kullan
                        c.setForeground(plTl >= 0 ? new Color(0, 100, 0) : new Color(139, 0, 0)); // Koyu yeşil/kırmızı
                    } else if (isSelected && ThemeManager.isDarkTheme()) {
                        // Koyu temada seçili satır: daha açık renkler kullan
                        c.setForeground(plTl >= 0 ? new Color(76, 175, 80) : new Color(244, 67, 54)); // Açık yeşil/kırmızı
                    } else {
                        // Seçili olmayan satır: normal renkler
                        c.setForeground(plTl >= 0 ? new Color(25, 135, 84) : new Color(178, 34, 34)); // Koyu yeşil/kırmızı
                    }
                }
                // Tüm veri hücrelerini sola hizala (buton sütunu hariç)
                if (c instanceof JLabel label && column != 6) {
                    label.setHorizontalAlignment(SwingConstants.LEFT);
                }
                return c;
            }
        };
        portfolioTable.setRowHeight(48); // Varlık ismi için daha yüksek satır
        portfolioTable.setShowGrid(false);
        portfolioTable.setFillsViewportHeight(false); // Boş alanları doldurma
        portfolioTable.setBackground(getPANEL_BG());
        portfolioTable.setForeground(getTEXT());
        // Tema değişimine göre dinamik seçim renkleri
        updateTableSelectionColors();
        portfolioTable.setBorder(new LineBorder(getBORDER()));
        
        // Odak çerçevesini kaldır - UIManager ile
        UIManager.put("Table.focusCellHighlightBorder", BorderFactory.createEmptyBorder());
        portfolioTable.putClientProperty("JTable.focusCellHighlightBorder", BorderFactory.createEmptyBorder());

        // Seçimi kaldırma özelliği: aynı satıra tekrar tıklayınca veya boş alana tıklayınca seçimi kaldır
        portfolioTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                int row = portfolioTable.rowAtPoint(e.getPoint());
                int col = portfolioTable.columnAtPoint(e.getPoint());

                // Boş alana tıklanmışsa (geçersiz satır), seçimi temizle
                if (row < 0) {
                    portfolioTable.clearSelection();
                    return;
                }

                // Geçerli bir satıra tıklanmışsa ve satır zaten seçiliyse, seçimleri temizle
                if (row >= 0 && row == portfolioTable.getSelectedRow()) {
                    // Sil butonu sütununa tıklanmadıysa (6. sütun) sadece seçimi kaldır
                    if (col != 6) {
                        portfolioTable.clearSelection();
                        return;
                    }
                }

                // Normal davranış: önce JTable'ın default işleyişine bırak
                super.mousePressed(e);
            }
        });

        // Tutar (Gizli) sütununu gizle (index 3)
        int tutarColumnIndex = -1;
        for (int i = 0; i < portfolioTable.getColumnCount(); i++) {
            if (portfolioTable.getColumnName(i).equals("Tutar (Gizli)")) {
                tutarColumnIndex = i;
                break;
            }
        }
        if (tutarColumnIndex >= 0) {
            portfolioTable.getColumnModel().removeColumn(portfolioTable.getColumnModel().getColumn(tutarColumnIndex));
        }
        
        // ASSET_ID sütununu gizle
        int assetIdColumnIndex = -1;
        for (int i = 0; i < portfolioTable.getColumnCount(); i++) {
            if (portfolioTable.getColumnName(i).equals("ASSET_ID")) {
                assetIdColumnIndex = i;
                break;
            }
        }
        if (assetIdColumnIndex >= 0) {
            portfolioTable.getColumnModel().removeColumn(portfolioTable.getColumnModel().getColumn(assetIdColumnIndex));
        }

        // Varlık sütunu için özel renderer (sembol + isim)
        portfolioTable.getColumnModel().getColumn(0).setCellRenderer(new AssetCellRenderer());

        // Sil butonu için renderer/editor
        portfolioTable.getColumnModel().getColumn(6).setCellRenderer((table, value, isSelected, hasFocus, row, column) -> {
            JButton btn = new JButton();
            btn.setIcon(new TrashIcon());
            btn.setToolTipText("Sil");
            styleButton(btn, new Color(178, 34, 34)); // Koyu kırmızı
            return btn;
        });
        portfolioTable.getColumnModel().getColumn(6).setCellEditor(new DeleteButtonEditor(new JCheckBox()));

        // Adet/Tutar sütunu için özel başlık renderer (dropdown menü ile)
        portfolioTable.getColumnModel().getColumn(2).setHeaderRenderer(new TableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JPanel panel = new JPanel(new BorderLayout());
                panel.setOpaque(true);
                panel.setBackground(getTABLE_HEADER());
                panel.setBorder(new EmptyBorder(0, 8, 0, 8));
                
                // Metin ve ikon için ayrı label'lar
                JLabel textLabel = new JLabel(showQuantityInsteadOfAmount ? "Adet" : "Tutar");
                textLabel.setFont(FONT_BOLD);
                textLabel.setForeground(getTEXT());
                textLabel.setHorizontalAlignment(SwingConstants.LEFT);
                
                // Dropdown ikonu
                JLabel iconLabel = new JLabel();
                iconLabel.setIcon(new DropdownIcon());
                iconLabel.setBorder(new EmptyBorder(0, 6, 0, 0));
                
                // İkon ve metni yan yana yerleştir
                JPanel contentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                contentPanel.setOpaque(false);
                contentPanel.add(textLabel);
                contentPanel.add(iconLabel);
                
                panel.add(contentPanel, BorderLayout.CENTER);
                return panel;
            }
        });

        JTableHeader header = portfolioTable.getTableHeader();
        header.setFont(FONT_BOLD);
        header.setBackground(getTABLE_HEADER());
        header.setForeground(getTEXT());
        header.setBorder(new LineBorder(getBORDER()));
        header.setReorderingAllowed(true); // Sütunlar yer değiştirilebilir

        // Başlık hücrelerini sola hizala ve padding ekle
        DefaultTableCellRenderer headerRenderer = (DefaultTableCellRenderer) header.getDefaultRenderer();
        headerRenderer.setHorizontalAlignment(SwingConstants.LEFT);
        headerRenderer.setBorder(new EmptyBorder(0, 8, 0, 8));

        // Başlık tıklama olayları
        header.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int col = header.columnAtPoint(e.getPoint());
                if (col == 2) { // Adet/Tutar sütunu - dropdown menü göster
                    showQuantityAmountDropdown(e.getComponent(), e.getX(), e.getY());
                } else if (col == 5) { // Kar/Zarar sütunu
                    showPlAsPercentage = !showPlAsPercentage;
                    // Tabloyu yeniden çiz
                    portfolioTable.repaint();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(portfolioTable);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(getBORDER(), 1, true),
                new EmptyBorder(8, 8, 8, 8)
        ));
        scroll.getViewport().setBackground(getPANEL_BG());
        // Scroll bar'ı sadece gerektiğinde göster
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        
        // Başlık satırı (başlık + butonlar)
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        titleBar.setBorder(new EmptyBorder(0, 2, 6, 2));
        
        JLabel title = new JLabel("Portföy Tablosu");
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(getTEXT());
        
        // Butonlar
        JButton btnRefresh = new JButton();
        btnRefresh.setIcon(new RefreshIcon());
        btnRefresh.setToolTipText("Yenile");
        btnRefresh.setPreferredSize(new Dimension(40, 35));
        styleButton(btnRefresh, new Color(40, 120, 200)); // Daha koyu mavi
        
        JButton btnAddTrade = new JButton("Yeni İşlem");
        // Buton genişliğini artır
        FontMetrics fm = btnAddTrade.getFontMetrics(btnAddTrade.getFont());
        int textWidth = fm.stringWidth("Yeni İşlem");
        btnAddTrade.setPreferredSize(new Dimension(textWidth + 50, 35)); // Metin genişliği + daha fazla padding
        styleButton(btnAddTrade, new Color(25, 135, 84)); // Koyu yeşil

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.setBorder(new EmptyBorder(0, 0, 0, 20)); // Sağdan boşluk ekleyerek butonları sola kaydır
        actions.add(btnRefresh);
        actions.add(btnAddTrade);
        
        // Buton aksiyonları
        btnAddTrade.addActionListener(e -> {
            JFrame parentFrame = getParentFrame();
            NewTradeDialog dlg = new NewTradeDialog(parentFrame, user, v -> {
                loadPortfolioFromDb();
            });
            dlg.setVisible(true);
        });
        btnRefresh.addActionListener(e -> {
            refreshPrices(false, btnRefresh); // Manuel yenileme - bildirim göster
        });
        titleBar.add(title, BorderLayout.WEST);
        titleBar.add(actions, BorderLayout.EAST);
        
        container.add(titleBar, BorderLayout.NORTH);
        container.add(scroll, BorderLayout.CENTER);

        return container;
    }
    private JPanel buildChartSection() {
        JPanel container = new JPanel(new BorderLayout(12, 0));
        container.setOpaque(false);
        container.setPreferredSize(new Dimension(0, 230));

        // Sol taraf: Halka grafik
        donutChartPanel = new DonutChartPanel();
        JPanel donutWrapper = new JPanel(new BorderLayout());
        donutWrapper.setOpaque(false);
        donutWrapper.setPreferredSize(new Dimension(350, 0)); // Genişliği artırdık
        donutWrapper.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(getBORDER(), 1, true),
                new EmptyBorder(10, 10, 10, 10)
        ));
        donutWrapper.setBackground(getPANEL_BG());
        
        JLabel donutTitle = new JLabel("Piyasa Dağılımı");
        donutTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        donutTitle.setForeground(getTEXT());
        donutTitle.setBorder(new EmptyBorder(0, 0, 8, 0));
        donutWrapper.add(donutTitle, BorderLayout.NORTH);
        donutWrapper.add(donutChartPanel, BorderLayout.CENTER);

        // Sağ taraf: Line chart
        lineChartPanel = new PortfolioLineChartPanel();
        chartPanel = new JPanel(new BorderLayout());
        chartPanel.setBackground(getPANEL_BG());
        chartPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(getBORDER(), 1, true),
                new EmptyBorder(10, 10, 10, 10)
        ));
        JLabel chartTitle = new JLabel("Portföy Değer Değişimi");
        chartTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        chartTitle.setForeground(getTEXT());
        chartTitle.setBorder(new EmptyBorder(0, 0, 8, 0));
        chartPanel.add(chartTitle, BorderLayout.NORTH);
        chartPanel.add(lineChartPanel, BorderLayout.CENTER);

        container.add(donutWrapper, BorderLayout.WEST);
        container.add(chartPanel, BorderLayout.CENTER);
        return container;
    }

    /**
     * Piyasa türlerine göre portföy dağılımını hesaplar ve halka grafiği günceller
     */
    private void updateDonutChart(java.util.List<dao.PortfolioDao.PortfolioRow> rows, PriceHistoryDao priceHistoryDao) {
        if (donutChartPanel == null) return;

        // Piyasa türlerine göre toplam değerleri hesapla
        double bistValue = 0.0;
        double usValue = 0.0;
        double cryptoValue = 0.0;
        double commodityValue = 0.0;

        for (var r : rows) {
            Double lastPrice = priceHistoryDao.findLatestPrice(r.assetId);
            double currentPrice = (lastPrice != null) ? lastPrice : r.avgCost;
            String market = r.market != null ? r.market.toUpperCase() : "";
            double fx = ("US".equals(market) || "CRYPTO".equals(market) || "COMMODITY".equals(market)) ? USD_TO_TRY : 1.0;
            double valueTl = currentPrice * fx * r.quantity;
            switch (market) {
                case "BIST":
                    bistValue += valueTl;
                    break;
                case "US":
                    usValue += valueTl;
                    break;
                case "CRYPTO":
                    cryptoValue += valueTl;
                    break;
                case "COMMODITY":
                    commodityValue += valueTl;
                    break;
            }
        }

        donutChartPanel.updateData(bistValue, usValue, cryptoValue, commodityValue);
    }

    private JPanel createMetricCard(String title, String value, String subtitle, Color accent) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(getPANEL_BG());
        card.setBorder(new CompoundBorder(
                new LineBorder(getBORDER(), 1, true),
                new EmptyBorder(10, 12, 10, 12)
        ));

        // Başlık paneli (başlık + göz ikonu)
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);
        
        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        // Portföy Değeri ve Toplam P/L başlıkları için
        if ("Portföy Değeri".equals(title) || "Toplam P/L".equals(title)) {
            lblTitle.setForeground(getTEXT());
        } else {
            lblTitle.setForeground(getTEXT_MUTED());
        }
        
        // Sadece Portföy Değeri kartına göz ikonu ekle
        if ("Portföy Değeri".equals(title)) {
            final JButton btnToggle = new JButton();
            btnToggle.setIcon(new EyeIcon(portfolioValueVisible));
            btnToggle.setToolTipText(portfolioValueVisible ? "Bakiyeyi Gizle" : "Bakiyeyi Göster");
            btnToggle.setPreferredSize(new Dimension(28, 28)); // Daha büyük buton
            btnToggle.setBackground(null);
            btnToggle.setBorderPainted(false);
            btnToggle.setFocusPainted(false);
            btnToggle.setOpaque(false);
            btnToggle.setCursor(new Cursor(Cursor.HAND_CURSOR));
            
            // Buton referansını sakla
            eyeIconButton1 = btnToggle;
            
            btnToggle.addActionListener(e -> {
                portfolioValueVisible = !portfolioValueVisible;
                // Göz ikonunu güncelle
                updateAllEyeIcons();
                // Değerleri güncelle
                refreshPortfolioValues();
                // Halka grafiği güncelle (gizleme durumuna göre)
                if (donutChartPanel != null) {
                    donutChartPanel.repaint();
                }
            });
            titlePanel.add(lblTitle, BorderLayout.WEST);
            titlePanel.add(btnToggle, BorderLayout.EAST);
        } else {
            titlePanel.add(lblTitle, BorderLayout.WEST);
        }

        JLabel lblValue = new JLabel(value);
        lblValue.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblValue.setForeground(getTEXT());

        JLabel lblSubtitle = new JLabel(subtitle);
        lblSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSubtitle.setForeground(accent);

        card.add(titlePanel);
        card.add(Box.createVerticalStrut(4));
        card.add(lblValue);
        card.add(Box.createVerticalStrut(3));
        card.add(lblSubtitle);

        // referansları sakla ki gerçek verilerle güncelleyebilelim
        if ("Portföy Değeri".equals(title)) {
            lblPortfolioValue = lblValue;
        } else if ("Toplam P/L".equals(title)) {
            lblTotalPl = lblValue;
        }

        return card;
    }
    
    // Göz ikonu butonunu sakla
    private JButton eyeIconButton1 = null;
    
    /**
     * Göz ikonunu günceller
     */
    private void updateAllEyeIcons() {
        if (eyeIconButton1 != null) {
            eyeIconButton1.setIcon(new EyeIcon(portfolioValueVisible));
            eyeIconButton1.setToolTipText(portfolioValueVisible ? "Bakiyeyi Gizle" : "Bakiyeyi Göster");
        }
    }
    
    /**
     * Portföy değerlerini günceller (gizleme durumuna göre)
     */
    private void refreshPortfolioValues() {
        // Mevcut değerleri al ve güncelle
        if (lblPortfolioValue != null && lblTotalPl != null) {
            // Değerleri tekrar hesapla
            dao.PortfolioDao dao = new dao.PortfolioDao();
            var rows = dao.getPortfolioSummary(user.getId());
            PriceHistoryDao priceHistoryDao = new PriceHistoryDao();
            
            double totalValue = 0.0;
            double totalCost = 0.0;
            
            for (var r : rows) {
                Double lastPrice = priceHistoryDao.findLatestPrice(r.assetId);
                double currentPrice = (lastPrice != null) ? lastPrice : r.avgCost;
                String market = r.market != null ? r.market.toUpperCase() : "";
                double fx = ("US".equals(market) || "CRYPTO".equals(market) || "COMMODITY".equals(market)) ? USD_TO_TRY : 1.0;
                double valueTl = currentPrice * fx * r.quantity;
                double costTl = r.avgCost * fx * r.quantity;
                totalValue += valueTl;
                totalCost += costTl;
            }
            
            updateMetrics(totalValue, totalValue - totalCost);
        }
    }

    private JPanel createWatchCard(String symbol, String price, String percent, boolean positive) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(getPANEL_BG());
        card.setBorder(new CompoundBorder(
                new EmptyBorder(0, 0, 10, 0),
                new LineBorder(getBORDER(), 1, true)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));

        JLabel lblSymbol = new JLabel(symbol);
        lblSymbol.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblSymbol.setForeground(getTEXT());

        JLabel lblPrice = new JLabel(price);
        lblPrice.setFont(FONT_BOLD);
        lblPrice.setForeground(getTEXT());
        lblPrice.setHorizontalAlignment(SwingConstants.RIGHT);

        JLabel lblPercent = new JLabel(percent);
        lblPercent.setFont(FONT_REGULAR);
        lblPercent.setForeground(positive ? new Color(25, 135, 84) : new Color(178, 34, 34)); // Koyu yeşil/kırmızı
        lblPercent.setHorizontalAlignment(SwingConstants.RIGHT);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBorder(new EmptyBorder(8, 10, 8, 0));
        left.add(lblSymbol);
        left.add(Box.createVerticalStrut(2));
        JLabel info = new JLabel("Canlı • TL");
        info.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        info.setForeground(getTEXT_MUTED());
        left.add(info);

        JPanel right = new JPanel(new GridLayout(2, 1));
        right.setOpaque(false);
        right.setBorder(new EmptyBorder(8, 10, 8, 10));
        right.add(lblPrice);
        right.add(lblPercent);

        card.add(left, BorderLayout.WEST);
        card.add(right, BorderLayout.EAST);

        return card;
    }

    private void refreshWatchlist() {
        if (watchListContent == null || portfolioModel == null) return;

        watchListContent.removeAll();

        for (int i = 0; i < portfolioModel.getRowCount(); i++) {
            Object symbolObj = portfolioModel.getValueAt(i, 0);
            Object qtyObj = portfolioModel.getValueAt(i, 2);
            Object avgCostObj = portfolioModel.getValueAt(i, 4);
            Object priceObj = portfolioModel.getValueAt(i, 5);
            Object plObj = portfolioModel.getValueAt(i, 6);

            String symbol = symbolObj != null ? symbolObj.toString() : "-";
            double qty = qtyObj instanceof Number ? ((Number) qtyObj).doubleValue() : 0;
            double avgCost = avgCostObj instanceof Number ? ((Number) avgCostObj).doubleValue() : 0;
            double price = priceObj instanceof Number ? ((Number) priceObj).doubleValue() : 0;
            double pl = plObj instanceof Number ? ((Number) plObj).doubleValue() : 0;

            String priceText = PRICE_FMT.format(price);
            double costBasis = qty * avgCost;
            double plPct = (costBasis > 0) ? (pl / costBasis) * 100.0 : 0.0;
            String pctText = (plPct >= 0 ? "+" : "") + PCT_FMT.format(plPct);
            boolean positive = plPct >= 0;

            watchListContent.add(createWatchCard(symbol, priceText, pctText, positive));
        }
        watchListContent.add(Box.createVerticalGlue());
        watchListContent.revalidate();
        watchListContent.repaint();
    }

    private void updateMetrics(double totalValue, double totalPl) {
        if (lblPortfolioValue != null) {
            if (portfolioValueVisible) {
                lblPortfolioValue.setText("₺" + PRICE_FMT.format(totalValue));
            } else {
                lblPortfolioValue.setText("••••••");
            }
        }
        if (lblTotalPl != null) {
            if (portfolioValueVisible) {
                String prefix = totalPl >= 0 ? "+₺" : "-₺";
                double absPl = Math.abs(totalPl);
                lblTotalPl.setText(prefix + PRICE_FMT.format(absPl));
            } else {
                lblTotalPl.setText("••••••");
            }
        }
    }

    /**
     * Adet/Tutar sütunu için dropdown menü gösterir
     */
    private void showQuantityAmountDropdown(Component invoker, int x, int y) {
        JPopupMenu popup = new JPopupMenu();
        
        JMenuItem adetItem = new JMenuItem("Adet");
        adetItem.addActionListener(e -> {
            showQuantityInsteadOfAmount = true;
            portfolioTable.repaint();
            // Başlığı da güncelle
            portfolioTable.getTableHeader().repaint();
        });
        if (showQuantityInsteadOfAmount) {
            Icon checkIcon = (Icon) javax.swing.UIManager.get("CheckBoxMenuItem.checkIcon");
            if (checkIcon != null) {
                adetItem.setIcon(checkIcon);
            }
        }
        popup.add(adetItem);
        
        JMenuItem tutarItem = new JMenuItem("Tutar");
        tutarItem.addActionListener(e -> {
            showQuantityInsteadOfAmount = false;
            portfolioTable.repaint();
            // Başlığı da güncelle
            portfolioTable.getTableHeader().repaint();
        });
        if (!showQuantityInsteadOfAmount) {
            Icon checkIcon = (Icon) javax.swing.UIManager.get("CheckBoxMenuItem.checkIcon");
            if (checkIcon != null) {
                tutarItem.setIcon(checkIcon);
            }
        }
        popup.add(tutarItem);
        
        popup.show(invoker, x, y);
    }

    /**
     * JTable'ın yüksekliğini satır sayısına göre dinamik olarak ayarlar.
     * Böylece alt tarafta boş alan kalmaz.
     */
    private void adjustTableHeight() {
        if (portfolioTable == null) return;

        int rowCount = portfolioModel.getRowCount();
        int rowHeight = portfolioTable.getRowHeight();
        JTableHeader header = portfolioTable.getTableHeader();
        int headerHeight = (header != null) ? header.getPreferredSize().height : 0;

        // Tablo yüksekliği = header yüksekliği + (satır sayısı * satır yüksekliği)
        int preferredHeight = headerHeight + (rowCount * rowHeight);

        // Minimum yükseklik ayarla (en az 1 satır görünsün)
        if (rowCount == 0) {
            preferredHeight = headerHeight + rowHeight;
        }

        // JTable'ın preferred size'ını ayarla
        portfolioTable.setPreferredScrollableViewportSize(
            new Dimension(portfolioTable.getPreferredSize().width, preferredHeight)
        );

        // Container'ı yeniden düzenle
        portfolioTable.getParent().revalidate();
        portfolioTable.getParent().repaint();
    }
    private void styleButton(JButton button, Color bgColor) {
        button.setFont(FONT_BOLD);
        button.setForeground(Color.WHITE);
        button.setBackground(bgColor);
        button.setFocusPainted(false);
        button.setOpaque(true);
        // FlatLaf için yuvarlatılmış köşeler
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        // Hover efekti için
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }
    
    /**
     * Tema toggle butonu oluşturur (güneş/ay ikonu)
     */
    private JButton createThemeToggleButton() {
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(40, 40));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setIcon(new ThemeIcon(ThemeManager.isDarkTheme()));
        
        button.addActionListener(e -> {
            ThemeManager.toggleTheme();
            applyTheme();
            button.setIcon(new ThemeIcon(ThemeManager.isDarkTheme()));
        });
        
        return button;
    }
    
    /**
     * Tüm UI bileşenlerine temayı uygular
     */
    private void applyTheme() {
        try {
            // FlatLaf tema değiştir
            if (ThemeManager.isDarkTheme()) {
                UIManager.setLookAndFeel(new FlatMacDarkLaf());
            } else {
                UIManager.setLookAndFeel(new FlatMacLightLaf());
            }
            SwingUtilities.updateComponentTreeUI(this);
            
            // Renkleri güncelle
            updateColors();
            
            // Grafikleri güncelle
            if (donutChartPanel != null) {
                donutChartPanel.repaint();
            }
            if (lineChartPanel != null) {
                updateLineChart();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Tablo seçim renklerini tema değişimine göre günceller
     */
    private void updateTableSelectionColors() {
        if (portfolioTable == null) return;
        
        if (ThemeManager.isDarkTheme()) {
            // Koyu tema: koyu gri arka plan, beyaz yazı
            portfolioTable.setSelectionBackground(new Color(45, 45, 45)); // #2d2d2d
            portfolioTable.setSelectionForeground(Color.WHITE);
        } else {
            // Açık tema: açık mavi arka plan, siyah yazı
            portfolioTable.setSelectionBackground(new Color(227, 242, 253)); // #E3F2FD
            portfolioTable.setSelectionForeground(Color.BLACK);
        }
    }
    
    /**
     * Tüm bileşenlerin renklerini günceller
     */
    private void updateColors() {
        // Root panel (artık MainPanel kendisi root)
        setBackground(getBG());
        
        // Tablo renkleri
        if (portfolioTable != null) {
            portfolioTable.setBackground(getPANEL_BG());
            portfolioTable.setForeground(getTEXT());
            // Tema değişimine göre dinamik seçim renkleri
            updateTableSelectionColors();
            portfolioTable.setBorder(new LineBorder(getBORDER()));
            
            JTableHeader header = portfolioTable.getTableHeader();
            if (header != null) {
                header.setBackground(getTABLE_HEADER());
                header.setForeground(getTEXT());
                header.setBorder(new LineBorder(getBORDER()));
            }
            
            // Tablo scroll pane
            Container tableParent = portfolioTable.getParent();
            if (tableParent instanceof JViewport) {
                JViewport viewport = (JViewport) tableParent;
                viewport.setBackground(getPANEL_BG());
                Container scrollParent = viewport.getParent();
                if (scrollParent instanceof JScrollPane) {
                    JScrollPane scroll = (JScrollPane) scrollParent;
                    scroll.setBorder(BorderFactory.createCompoundBorder(
                            new LineBorder(getBORDER(), 1, true),
                            new EmptyBorder(8, 8, 8, 8)
                    ));
                }
            }
            
            portfolioTable.repaint();
        }
        
        // Tüm bileşenleri recursive olarak güncelle
        updateAllComponents(this);
        
        // Metrik kartlarını yeniden oluştur
        refreshMetricCards();
        
        // Grafik panellerini güncelle
        if (donutChartPanel != null) {
            donutChartPanel.repaint();
        }
        if (lineChartPanel != null) {
            // Line chart'ı verilerle birlikte yeniden oluştur (tema değişimine uyum için)
            updateLineChart();
        }
        
        repaint();
    }
    
    /**
     * Tüm bileşenleri recursive olarak günceller
     */
    private void updateAllComponents(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JPanel) {
                JPanel panel = (JPanel) comp;
                Color bg = panel.getBackground();
                // Açık tema renklerini koyu temaya çevir veya tersi
                if (bg != null) {
                    if (bg.equals(Color.WHITE) || bg.equals(new Color(248, 249, 250)) || 
                        bg.equals(new Color(240, 242, 245))) {
                        panel.setBackground(getPANEL_BG());
                    } else if (bg.equals(new Color(18, 18, 18)) || bg.equals(new Color(30, 30, 30)) ||
                               bg.equals(new Color(40, 40, 40)) || bg.equals(new Color(50, 50, 50))) {
                        panel.setBackground(getPANEL_BG());
                    }
                }
                // Border'ları güncelle
                if (panel.getBorder() instanceof CompoundBorder) {
                    CompoundBorder cb = (CompoundBorder) panel.getBorder();
                    if (cb.getOutsideBorder() instanceof LineBorder) {
                        LineBorder lb = (LineBorder) cb.getOutsideBorder();
                        panel.setBorder(new CompoundBorder(
                                new LineBorder(getBORDER(), lb.getThickness(), lb.getRoundedCorners()),
                                cb.getInsideBorder()
                        ));
                    }
                } else if (panel.getBorder() instanceof LineBorder) {
                    LineBorder lb = (LineBorder) panel.getBorder();
                    panel.setBorder(new LineBorder(getBORDER(), lb.getThickness(), lb.getRoundedCorners()));
                }
            }
            if (comp instanceof JLabel) {
                JLabel label = (JLabel) comp;
                Color fg = label.getForeground();
                if (fg != null) {
                    // Açık tema metin renklerini güncelle
                    if (fg.equals(new Color(33, 37, 41))) {
                        label.setForeground(getTEXT());
                    } else if (fg.equals(new Color(108, 117, 125))) {
                        label.setForeground(getTEXT_MUTED());
                    }
                    // Koyu tema metin renklerini güncelle
                    if (fg.equals(Color.WHITE) && !ThemeManager.isDarkTheme()) {
                        label.setForeground(getTEXT());
                    } else if (fg.equals(new Color(180, 180, 180)) && !ThemeManager.isDarkTheme()) {
                        label.setForeground(getTEXT_MUTED());
                    }
                }
            }
            if (comp instanceof JScrollPane) {
                JScrollPane scroll = (JScrollPane) comp;
                scroll.getViewport().setBackground(getPANEL_BG());
                if (scroll.getBorder() instanceof CompoundBorder) {
                    CompoundBorder cb = (CompoundBorder) scroll.getBorder();
                    if (cb.getOutsideBorder() instanceof LineBorder) {
                        scroll.setBorder(BorderFactory.createCompoundBorder(
                                new LineBorder(getBORDER(), 1, true),
                                cb.getInsideBorder()
                        ));
                    }
                }
            }
            if (comp instanceof Container) {
                updateAllComponents((Container) comp);
            }
        }
    }
    
    /**
     * Metrik kartlarını yeniden oluşturur
     */
    private void refreshMetricCards() {
        // Header'daki metrik kartlarını bul ve güncelle
        Component[] components = getComponents();
        for (Component comp : components) {
            if (comp instanceof JPanel) {
                findAndUpdateMetricCards((JPanel) comp);
            }
        }
    }
    
    /**
     * Metrik kartlarını bulur ve günceller
     */
    private void findAndUpdateMetricCards(JPanel panel) {
        for (Component comp : panel.getComponents()) {
            if (comp instanceof JPanel) {
                JPanel card = (JPanel) comp;
                // Metrik kartı olup olmadığını kontrol et (border ve layout'a göre)
                if (card.getBorder() instanceof CompoundBorder) {
                    card.setBackground(getPANEL_BG());
                    CompoundBorder border = (CompoundBorder) card.getBorder();
                    if (border.getOutsideBorder() instanceof LineBorder) {
                        card.setBorder(new CompoundBorder(
                                new LineBorder(getBORDER(), 1, true),
                                border.getInsideBorder()
                        ));
                    }
                    // Kart içindeki label'ları güncelle
                    updateCardLabels(card);
                }
            }
            if (comp instanceof Container && comp instanceof JPanel) {
                findAndUpdateMetricCards((JPanel) comp);
            }
        }
    }
    
    /**
     * Kart içindeki label'ları günceller
     */
    private void updateCardLabels(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JLabel) {
                JLabel label = (JLabel) comp;
                Font font = label.getFont();
                if (font != null && font.isBold() && font.getSize() == 18) {
                    // Değer label'ı
                    label.setForeground(getTEXT());
                } else if (font != null && font.getSize() == 12) {
                    // Başlık veya alt başlık
                    Color currentFg = label.getForeground();
                    if (currentFg != null) {
                        if (currentFg.equals(new Color(33, 37, 41)) || currentFg.equals(Color.WHITE)) {
                            label.setForeground(getTEXT());
                        } else if (currentFg.equals(new Color(108, 117, 125)) || 
                                   currentFg.equals(new Color(180, 180, 180))) {
                            label.setForeground(getTEXT_MUTED());
                        }
                    }
                }
            }
            if (comp instanceof Container) {
                updateCardLabels((Container) comp);
            }
        }
    }
    
    /**
     * Tema toggle ikonu (güneş/ay)
     */
    private static class ThemeIcon implements javax.swing.Icon {
        private final boolean isDark;
        private static final int SIZE = 24;
        
        public ThemeIcon(boolean isDark) {
            this.isDark = isDark;
        }
        
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int centerX = x + SIZE / 2;
            int centerY = y + SIZE / 2;
            
            if (isDark) {
                // Ay ikonu (karanlık tema aktif - ay göster)
                g2.setColor(new Color(255, 255, 200)); // Açık sarı
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                
                // Ay şekli (yarım daire + iç yarım daire)
                g2.drawArc(centerX - 6, centerY - 6, 12, 12, 30, 180);
                g2.drawArc(centerX - 4, centerY - 6, 8, 12, 30, 180);
                
                // Yıldızlar
                g2.setColor(new Color(255, 255, 200));
                g2.fillOval(centerX - 8, centerY - 4, 2, 2);
                g2.fillOval(centerX + 6, centerY - 2, 2, 2);
            } else {
                // Güneş ikonu (açık tema aktif - güneş göster)
                g2.setColor(new Color(255, 200, 0)); // Sarı
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                
                // Güneş merkezi
                g2.fillOval(centerX - 5, centerY - 5, 10, 10);
                
                // Güneş ışınları
                g2.setColor(new Color(255, 200, 0));
                for (int i = 0; i < 8; i++) {
                    double angle = Math.PI * 2 * i / 8;
                    int x1 = centerX + (int) (7 * Math.cos(angle));
                    int y1 = centerY + (int) (7 * Math.sin(angle));
                    int x2 = centerX + (int) (10 * Math.cos(angle));
                    int y2 = centerY + (int) (10 * Math.sin(angle));
                    g2.drawLine(x1, y1, x2, y2);
                }
            }
            
            g2.dispose();
        }
        
        @Override
        public int getIconWidth() {
            return SIZE;
        }
        
        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
    
    /**
     * Göz ikonu çizen sınıf (açık/kapalı göz)
     */
    private static class EyeIcon implements javax.swing.Icon {
        private final boolean isOpen;

        public EyeIcon(boolean isOpen) {
            this.isOpen = isOpen;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getIconWidth();
            int height = getIconHeight();
            int centerX = x + width / 2;
            int centerY = y + height / 2;

            g2.setColor(new Color(108, 117, 125)); // TEXT_MUTED rengi

            if (isOpen) {
                // Açık göz: göz şekli + iris
                // Göz şekli (elips) - daha büyük
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(x + 3, y + 5, width - 6, height - 10);
                
                // İris (daire) - daha büyük
                int irisSize = 8;
                g2.fillOval(centerX - irisSize / 2, centerY - irisSize / 2, irisSize, irisSize);
            } else {
                // Kapalı göz: çizgi (göz üzerinde çapraz çizgi efekti)
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // Göz şekli (elips) - daha büyük
                g2.drawOval(x + 3, y + 5, width - 6, height - 10);
                // Çapraz çizgi (gözü kapatan)
                g2.drawLine(x + 3, y + 5, x + width - 3, y + height - 5);
            }

            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 24; // Daha büyük
        }

        @Override
        public int getIconHeight() {
            return 18; // Daha büyük
        }
    }

    /**
     * Yenileme (refresh) ikonu çizen sınıf
     */
    private static class RefreshIcon implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getIconWidth();
            int height = getIconHeight();
            int centerX = x + width / 2;
            int centerY = y + height / 2;
            int radius = Math.min(width, height) / 2 - 2;

            // Mavi daire arka plan
            g2.setColor(new Color(40, 120, 200)); // Daha koyu mavi
            g2.fillOval(x, y, width, height);

            // Beyaz döngüsel ok
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            
            // Döngüsel ok çiz (yaklaşık 270 derece, saat yönünde)
            int startAngle = 30; // Sağ üstten başla
            int arcAngle = 270; // 270 derece çiz
            
            // Yay çiz
            g2.drawArc(centerX - radius + 1, centerY - radius + 1, (radius - 1) * 2, (radius - 1) * 2, startAngle, arcAngle);
            
            // Ok başı (sağ üstte, yayın sonu)
            double angle1 = Math.toRadians(startAngle);
            int arrowX1 = centerX + (int) ((radius - 1) * Math.cos(angle1));
            int arrowY1 = centerY - (int) ((radius - 1) * Math.sin(angle1));
            
            // Ok başı çizgileri (V şeklinde)
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // Ok başının sol üst çizgisi
            double arrowAngle1 = angle1 + Math.PI * 0.75; // 135 derece
            int arrowEndX1 = arrowX1 + (int) (5 * Math.cos(arrowAngle1));
            int arrowEndY1 = arrowY1 - (int) (5 * Math.sin(arrowAngle1));
            g2.drawLine(arrowX1, arrowY1, arrowEndX1, arrowEndY1);
            
            // Ok başının sol alt çizgisi
            double arrowAngle2 = angle1 + Math.PI * 1.25; // 225 derece
            int arrowEndX2 = arrowX1 + (int) (5 * Math.cos(arrowAngle2));
            int arrowEndY2 = arrowY1 - (int) (5 * Math.sin(arrowAngle2));
            g2.drawLine(arrowX1, arrowY1, arrowEndX2, arrowEndY2);

            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 20;
        }

        @Override
        public int getIconHeight() {
            return 20;
        }
    }

    /**
     * Çöp kutusu ikonu çizen sınıf - tema değişimine uyumlu
     */
    private static class TrashIcon implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getIconWidth();
            int height = getIconHeight();
            int centerX = x + width / 2;
            
            // Tema değişimine göre renk seç
            Color iconColor = ThemeManager.isDarkTheme() 
                ? new Color(220, 220, 220) // Koyu temada açık gri
                : new Color(33, 37, 41);   // Açık temada koyu gri

            // Çöp kutusu gövdesi (dikdörtgen)
            g2.setColor(iconColor);
            g2.fillRect(x + 2, y + 6, width - 4, height - 10);
            g2.setColor(iconColor);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRect(x + 2, y + 6, width - 4, height - 10);

            // Çöp kutusu kapağı (üst kısım)
            g2.fillRect(x + 1, y + 4, width - 2, 3);
            g2.drawRect(x + 1, y + 4, width - 2, 3);

            // Çöp kutusu tutamacı (üstte küçük çizgi)
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(centerX - 3, y + 2, centerX + 3, y + 2);

            // Çöp kutusu içindeki çizgiler (çöp görünümü)
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(centerX - 2, y + 8, centerX - 2, y + height - 4);
            g2.drawLine(centerX, y + 8, centerX, y + height - 4);
            g2.drawLine(centerX + 2, y + 8, centerX + 2, y + height - 4);

            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }

    /**
     * Dropdown ok ikonu - daha belirgin ve göze hitap eden tasarım
     */
    private static class DropdownIcon implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int width = getIconWidth();
            int height = getIconHeight();
            int centerX = x + width / 2;
            int centerY = y + height / 2;

            // Tema değişimine göre renk seç
            Color iconColor = ThemeManager.isDarkTheme() 
                ? new Color(180, 200, 255) // Koyu temada açık mavi
                : new Color(70, 130, 180);  // Açık temada koyu mavi

            // Daha kalın ve belirgin ok çizimi
            g2.setColor(iconColor);
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // Aşağı yönlü ok (üçgen şeklinde)
            int arrowSize = Math.min(width, height) - 4;
            int[] xPoints = {
                centerX - arrowSize / 2,
                centerX,
                centerX + arrowSize / 2
            };
            int[] yPoints = {
                centerY - arrowSize / 3,
                centerY + arrowSize / 3,
                centerY - arrowSize / 3
            };
            
            // Ok ucu doldurulmuş üçgen
            g2.fillPolygon(xPoints, yPoints, 3);
            
            // Daha belirgin görünüm için hafif gölge efekti
            g2.setColor(new Color(iconColor.getRed(), iconColor.getGreen(), iconColor.getBlue(), 100));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawPolygon(xPoints, yPoints, 3);

            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 14;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }

    /**
     * Varlık sütunu için özel renderer (sembol + isim)
     */
    private class AssetCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                      boolean hasFocus, int row, int column) {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setOpaque(true);
            panel.setBorder(new EmptyBorder(4, 8, 4, 8));
            
            // Arka plan rengi
            if (isSelected) {
                if (ThemeManager.isDarkTheme()) {
                    panel.setBackground(new Color(45, 45, 45));
                } else {
                    panel.setBackground(new Color(227, 242, 253));
                }
            } else {
                panel.setBackground((row % 2 == 0) ? getPANEL_BG() : getTABLE_ROW_ALT());
            }
            
            // Sembol ve isim bilgilerini al
            String symbol = "";
            String name = "";
            if (value instanceof String[]) {
                String[] assetInfo = (String[]) value;
                if (assetInfo.length >= 2) {
                    symbol = assetInfo[0] != null ? assetInfo[0] : "";
                    name = assetInfo[1] != null ? assetInfo[1] : "";
                }
            } else if (value != null) {
                symbol = value.toString();
            }
            
            // Sembol label'ı (kalın, üstte)
            JLabel symbolLabel = new JLabel(symbol);
            symbolLabel.setFont(FONT_BOLD);
            symbolLabel.setForeground(isSelected ? (ThemeManager.isDarkTheme() ? Color.WHITE : Color.BLACK) : getTEXT());
            symbolLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            // İsim label'ı (küçük, hemen altında, muted renk)
            JLabel nameLabel = new JLabel(name != null && !name.isEmpty() ? name : "-");
            nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            nameLabel.setForeground(getTEXT_MUTED());
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            panel.add(symbolLabel);
            panel.add(Box.createVerticalStrut(2)); // Çok küçük boşluk (2 piksel)
            panel.add(nameLabel);
            
            return panel;
        }
    }

    /**
     * "Sil" sütunu için hücre editörü.
     * Butona tıklandığında ilgili varlığın tüm işlemlerini siler ve portföyü yeniden yükler.
     */
    private class DeleteButtonEditor extends AbstractCellEditor implements TableCellEditor, java.awt.event.ActionListener {
        private final JButton button;
        private int row;

        public DeleteButtonEditor(JCheckBox checkBox) {
            this.button = new JButton();
            this.button.setIcon(new TrashIcon());
            this.button.setToolTipText("Sil");
            styleButton(button, new Color(239, 83, 80));
            button.addActionListener(this);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int column) {
            this.row = row;
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            return "Sil";
        }

        @Override
        public void actionPerformed(java.awt.event.ActionEvent e) {
            int modelRow = portfolioTable.convertRowIndexToModel(row);
            Object assetIdObj = portfolioModel.getValueAt(modelRow, 8); // gizli ASSET_ID sütunu
            if (assetIdObj instanceof Number) {
                int assetId = ((Number) assetIdObj).intValue();

                // Özel onay dialog'u göster
                boolean confirmed = showDeleteConfirmationDialog();
                if (confirmed) {
                    // Önce hücre editörünü kapat
                    fireEditingStopped();

                    // DB'den sil ve portföyü yeniden yükle
                    TradeDao tradeDao = new TradeDao();
                    tradeDao.deleteAllByUserAndAsset(user.getId(), assetId);
                    loadPortfolioFromDb();
                }
            }
        }
    }

    /**
     * Bilgi mesaj dialog'u - koyu tema
     */
    private void showInfoDialog(String message) {
        JFrame parentFrame = getParentFrame();
        JDialog dialog = new JDialog(parentFrame, "", true);
        dialog.setUndecorated(true); // İşletim sistemi başlık çubuğunu kaldır
        dialog.setLocationRelativeTo(parentFrame);
        dialog.setResizable(false);

        // Koyu tema renkleri
        Color DARK_BG = new Color(18, 18, 18); // Koyu arka plan
        Color DARK_TEXT = new Color(255, 255, 255); // Beyaz metin
        Color DARK_HEADER = new Color(35, 35, 35); // Daha koyu başlık çubuğu
        Color DARK_BORDER = new Color(60, 60, 60); // Border rengi

        // Ana içerik paneli
        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setBackground(DARK_BG);
        content.setBorder(new LineBorder(DARK_BORDER, 1));
        
        // Özel başlık çubuğu (sürüklenebilir)
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(DARK_HEADER);
        titleBar.setPreferredSize(new Dimension(0, 35));
        titleBar.setBorder(new EmptyBorder(0, 10, 0, 5));
        
        // Dialog'u sürüklenebilir yap
        final int[] dragOffset = new int[2];
        titleBar.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                dragOffset[0] = e.getX();
                dragOffset[1] = e.getY();
            }
        });
        titleBar.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                Point currentLocation = dialog.getLocation();
                dialog.setLocation(
                    currentLocation.x + e.getX() - dragOffset[0],
                    currentLocation.y + e.getY() - dragOffset[1]
                );
            }
        });
        
        // Başlık
        JLabel titleLabel = new JLabel("Bilgi");
        titleLabel.setForeground(DARK_TEXT);
        titleLabel.setFont(FONT_REGULAR);
        titleBar.add(titleLabel, BorderLayout.WEST);
        
        // Kapatma butonu (X) - özel çizim ile
        final Color[] closeButtonColor = {DARK_TEXT}; // Hover için renk değişkeni
        JButton btnClose = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                if (isOpaque()) {
                    super.paintComponent(g);
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int size = 12;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                
                g2.setColor(closeButtonColor[0]);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // X çizgileri
                g2.drawLine(x, y, x + size, y + size);
                g2.drawLine(x + size, y, x, y + size);
                g2.dispose();
            }
        };
        btnClose.setBackground(DARK_HEADER);
        btnClose.setBorderPainted(false);
        btnClose.setFocusPainted(false);
        btnClose.setOpaque(false);
        btnClose.setPreferredSize(new Dimension(30, 30));
        btnClose.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnClose.addActionListener(e -> dialog.dispose());
        
        // Hover efekti
        btnClose.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btnClose.setBackground(new Color(60, 60, 60));
                btnClose.setOpaque(true);
                closeButtonColor[0] = Color.WHITE; // Hover'da beyaz
                btnClose.repaint();
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btnClose.setBackground(DARK_HEADER);
                btnClose.setOpaque(false);
                closeButtonColor[0] = DARK_TEXT; // Normal durumda açık gri
                btnClose.repaint();
            }
        });
        
        titleBar.add(btnClose, BorderLayout.EAST);
        
        // İçerik paneli
        JPanel innerContent = new JPanel(new BorderLayout(20, 20));
        innerContent.setBackground(DARK_BG);
        innerContent.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Mesaj paneli
        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.setOpaque(false);

        // Bilgi ikonu (yeşil daire içinde ✓)
        JLabel iconLabel = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int size = 40;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                
                // Yeşil daire
                g2.setColor(new Color(25, 135, 84));
                g2.fillOval(x, y, size, size);
                
                // Beyaz tik işareti
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int centerX = x + size / 2;
                int centerY = y + size / 2;
                g2.drawLine(centerX - 8, centerY, centerX - 3, centerY + 6);
                g2.drawLine(centerX - 3, centerY + 6, centerX + 8, centerY - 6);
                
                g2.dispose();
            }
        };
        iconLabel.setPreferredSize(new Dimension(60, 60));

        // Mesaj metni - sola hizalı
        JLabel messageLabel = new JLabel("<html><div style='text-align: left;'>" + message + "</div></html>");
        messageLabel.setFont(FONT_REGULAR);
        messageLabel.setForeground(DARK_TEXT);
        messageLabel.setHorizontalAlignment(SwingConstants.LEFT);

        JPanel centerPanel = new JPanel(new BorderLayout(15, 0));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(new EmptyBorder(0, 20, 0, 0)); // Sol padding ekle
        centerPanel.add(iconLabel, BorderLayout.WEST);
        centerPanel.add(messageLabel, BorderLayout.CENTER);
        
        messagePanel.add(centerPanel, BorderLayout.CENTER);

        // Buton paneli
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setOpaque(false);

        JButton btnOK = new JButton("Tamam");
        btnOK.setPreferredSize(new Dimension(100, 35));
        btnOK.setBackground(new Color(66, 153, 225)); // Mavi
        btnOK.setForeground(Color.WHITE);
        btnOK.setFont(FONT_BOLD);
        btnOK.setFocusPainted(false);
        btnOK.setOpaque(true);
        btnOK.setBorderPainted(false);
        btnOK.setCursor(new Cursor(Cursor.HAND_CURSOR));
        // Yuvarlatılmış köşeler için
        btnOK.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);

        btnOK.addActionListener(ev -> dialog.dispose());

        buttonPanel.add(btnOK);

        innerContent.add(messagePanel, BorderLayout.CENTER);
        innerContent.add(buttonPanel, BorderLayout.SOUTH);
        
        // Başlık çubuğu ve içeriği birleştir
        content.add(titleBar, BorderLayout.NORTH);
        content.add(innerContent, BorderLayout.CENTER);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(btnOK);
        
        // Dialog boyutunu ayarla
        dialog.setSize(400, 150);
        dialog.setVisible(true);
    }

    /**
     * Hata mesaj dialog'u - uygulamanın açık temasına uygun
     */
    private void showErrorDialog(String message) {
        JFrame parentFrame = getParentFrame();
        JDialog dialog = new JDialog(parentFrame, "Hata", true);
        dialog.setSize(400, 150);
        dialog.setLocationRelativeTo(parentFrame);
        dialog.setResizable(false);

        JPanel content = new JPanel(new BorderLayout(20, 20));
        content.setBackground(getBG());
        content.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Mesaj paneli
        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.setOpaque(false);

        // Hata ikonu (kırmızı daire içinde X)
        JLabel iconLabel = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int size = 40;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                
                // Kırmızı daire
                g2.setColor(new Color(178, 34, 34));
                g2.fillOval(x, y, size, size);
                
                // Beyaz X işareti
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int centerX = x + size / 2;
                int centerY = y + size / 2;
                int offset = 10;
                g2.drawLine(centerX - offset, centerY - offset, centerX + offset, centerY + offset);
                g2.drawLine(centerX + offset, centerY - offset, centerX - offset, centerY + offset);
                
                g2.dispose();
            }
        };
        iconLabel.setPreferredSize(new Dimension(60, 60));

        // Mesaj metni
        JLabel messageLabel = new JLabel("<html><div style='text-align: center;'>" + message + "</div></html>");
        messageLabel.setFont(FONT_REGULAR);
        messageLabel.setForeground(getTEXT());
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel centerPanel = new JPanel(new BorderLayout(15, 0));
        centerPanel.setOpaque(false);
        centerPanel.add(iconLabel, BorderLayout.WEST);
        centerPanel.add(messageLabel, BorderLayout.CENTER);
        
        messagePanel.add(centerPanel, BorderLayout.CENTER);

        // Buton paneli
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setOpaque(false);

        JButton btnOK = new JButton("Tamam");
        btnOK.setPreferredSize(new Dimension(100, 35));
        styleButton(btnOK, new Color(178, 34, 34)); // Kırmızı

        btnOK.addActionListener(ev -> dialog.dispose());

        buttonPanel.add(btnOK);

        content.add(messagePanel, BorderLayout.CENTER);
        content.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(btnOK);
        dialog.setVisible(true);
    }

    /**
     * Özel onay dialog'u - koyu tema
     */
    private boolean showDeleteConfirmationDialog() {
        JFrame parentFrame = getParentFrame();
        JDialog dialog = new JDialog(parentFrame, "", true);
        dialog.setUndecorated(true); // İşletim sistemi başlık çubuğunu kaldır
        dialog.setLocationRelativeTo(parentFrame);
        dialog.setResizable(false);

        // Koyu tema renkleri
        Color DARK_BG = new Color(45, 45, 45);
        Color DARK_TEXT = new Color(220, 220, 220);
        Color DARK_HEADER = new Color(35, 35, 35); // Daha koyu başlık çubuğu
        Color DARK_BORDER = new Color(80, 80, 80);

        // Ana içerik paneli
        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setBackground(DARK_BG);
        content.setBorder(new LineBorder(DARK_BORDER, 1));
        
        // Özel başlık çubuğu (sürüklenebilir)
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(DARK_HEADER);
        titleBar.setPreferredSize(new Dimension(0, 35));
        titleBar.setBorder(new EmptyBorder(0, 10, 0, 5));
        
        // Dialog'u sürüklenebilir yap
        final int[] dragOffset = new int[2];
        titleBar.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                dragOffset[0] = e.getX();
                dragOffset[1] = e.getY();
            }
        });
        titleBar.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                Point currentLocation = dialog.getLocation();
                dialog.setLocation(
                    currentLocation.x + e.getX() - dragOffset[0],
                    currentLocation.y + e.getY() - dragOffset[1]
                );
            }
        });
        
        // Başlık (boş, sadece görsel denge için)
        JLabel titleLabel = new JLabel(" ");
        titleLabel.setForeground(DARK_TEXT);
        titleLabel.setFont(FONT_REGULAR);
        titleBar.add(titleLabel, BorderLayout.WEST);
        
        // Kapatma butonu (X) - özel çizim ile
        final Color[] closeButtonColor = {DARK_TEXT}; // Hover için renk değişkeni
        JButton btnClose = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                if (isOpaque()) {
                    super.paintComponent(g);
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int size = 12;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                
                g2.setColor(closeButtonColor[0]);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // X çizgileri
                g2.drawLine(x, y, x + size, y + size);
                g2.drawLine(x + size, y, x, y + size);
                g2.dispose();
            }
        };
        btnClose.setBackground(DARK_HEADER);
        btnClose.setBorderPainted(false);
        btnClose.setFocusPainted(false);
        btnClose.setOpaque(false);
        btnClose.setPreferredSize(new Dimension(30, 30));
        btnClose.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnClose.addActionListener(e -> dialog.dispose());
        
        // Hover efekti
        btnClose.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btnClose.setBackground(new Color(60, 60, 60));
                btnClose.setOpaque(true);
                closeButtonColor[0] = Color.WHITE; // Hover'da beyaz
                btnClose.repaint();
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btnClose.setBackground(DARK_HEADER);
                btnClose.setOpaque(false);
                closeButtonColor[0] = DARK_TEXT; // Normal durumda açık gri
                btnClose.repaint();
            }
        });
        
        titleBar.add(btnClose, BorderLayout.EAST);
        
        // İçerik paneli
        JPanel innerContent = new JPanel(new BorderLayout(20, 20));
        innerContent.setBackground(DARK_BG);
        innerContent.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Mesaj paneli
        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.setOpaque(false);
        // Mesaj paneline padding ekle ki metin kenarlardan taşmasın
        messagePanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Mesaj metni - HTML ile düzgün görüntüleme için padding ve line-height ekle
        JLabel messageLabel = new JLabel("<html><div style='text-align: center; padding: 5px 15px; line-height: 1.6; width: 360px;'>" +
                "Bu varlığa ait tüm işlemler silinecek.<br>Emin misiniz?</div></html>");
        messageLabel.setFont(FONT_REGULAR);
        messageLabel.setForeground(DARK_TEXT);
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        messageLabel.setVerticalAlignment(SwingConstants.CENTER);

        messagePanel.add(messageLabel, BorderLayout.CENTER);

        // Buton paneli
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setOpaque(false);

        JButton btnEvet = new JButton("Evet");
        JButton btnHayir = new JButton("Hayır");
        btnEvet.setPreferredSize(new Dimension(100, 35));
        btnHayir.setPreferredSize(new Dimension(100, 35));
        styleButton(btnEvet, new Color(25, 135, 84)); // Yeşil
        styleButton(btnHayir, new Color(178, 34, 34)); // Kırmızı

        final boolean[] result = new boolean[1];

        btnEvet.addActionListener(ev -> {
            result[0] = true;
            dialog.dispose();
        });

        btnHayir.addActionListener(ev -> {
            result[0] = false;
            dialog.dispose();
        });

        buttonPanel.add(btnEvet);
        buttonPanel.add(btnHayir);

        innerContent.add(messagePanel, BorderLayout.CENTER);
        innerContent.add(buttonPanel, BorderLayout.SOUTH);
        
        // Başlık çubuğu ve içeriği birleştir
        content.add(titleBar, BorderLayout.NORTH);
        content.add(innerContent, BorderLayout.CENTER);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(btnEvet);
        
        // Dialog boyutunu içeriğe göre ayarla
        dialog.pack();
        // Minimum boyut ayarla (metnin tam görünmesi için)
        Dimension currentSize = dialog.getSize();
        int minWidth = Math.max(400, currentSize.width);
        int minHeight = Math.max(150, currentSize.height);
        dialog.setMinimumSize(new Dimension(minWidth, minHeight));
        dialog.setSize(minWidth, minHeight);
        // Dialog'un ekran dışına taşmamasını sağla
        dialog.setLocationRelativeTo(parentFrame);
        
        dialog.setVisible(true);

        return result[0];
    }
    /**
     * Halka grafik (donut chart) çizen panel
     */
    private class DonutChartPanel extends JPanel {
        private double bistValue = 0;
        private double usValue = 0;
        private double cryptoValue = 0;
        private double commodityValue = 0;

        // Piyasa renkleri
        private static final Color COLOR_BIST = new Color(66, 153, 225); // Mavi
        private static final Color COLOR_US = new Color(25, 135, 84); // Yeşil
        private static final Color COLOR_CRYPTO = new Color(255, 193, 7); // Sarı/Altın
        private static final Color COLOR_COMMODITY = new Color(220, 53, 69); // Kırmızı

        public DonutChartPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(320, 200)); // Genişliği artırdık
        }

        public void updateData(double bist, double us, double crypto, double commodity) {
            this.bistValue = bist;
            this.usValue = us;
            this.cryptoValue = crypto;
            this.commodityValue = commodity;
            repaint();
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            // Grafiği sola kaydırıp sağa daha fazla alan veriyoruz
            int centerX = width / 3; // Merkezi sola kaydırdık
            int centerY = height / 2;
            int radius = Math.min(width / 2, height) / 2 - 10; // Biraz daha büyük
            int innerRadius = radius - 35; // Halka kalınlığı

            double total = bistValue + usValue + cryptoValue + commodityValue;
            if (total == 0) {
                // Veri yoksa boş halka göster
                g2.setColor(getBORDER());
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
                
                g2.setColor(getTEXT_MUTED());
                g2.setFont(FONT_REGULAR);
                FontMetrics fm = g2.getFontMetrics();
                String noDataText = "Veri yok";
                int textWidth = fm.stringWidth(noDataText);
                int textHeight = fm.getHeight();
                g2.drawString(noDataText, centerX - textWidth / 2, centerY + textHeight / 4);
                g2.dispose();
                return;
            }

            double startAngle = -90; // 12'den başla
            double bistAngle = (bistValue / total) * 360;
            double usAngle = (usValue / total) * 360;
            double cryptoAngle = (cryptoValue / total) * 360;
            double commodityAngle = (commodityValue / total) * 360;

            // BIST
            if (bistValue > 0) {
                g2.setColor(COLOR_BIST);
                g2.fillArc(centerX - radius, centerY - radius, radius * 2, radius * 2,
                        (int) startAngle, (int) Math.ceil(bistAngle));
                g2.setColor(COLOR_BIST.darker());
                g2.setStroke(new BasicStroke(2f));
                g2.drawArc(centerX - radius, centerY - radius, radius * 2, radius * 2,
                        (int) startAngle, (int) Math.ceil(bistAngle));
                startAngle += bistAngle;
            }

            // US
            if (usValue > 0) {
                g2.setColor(COLOR_US);
                g2.fillArc(centerX - radius, centerY - radius, radius * 2, radius * 2,
                        (int) startAngle, (int) Math.ceil(usAngle));
                g2.setColor(COLOR_US.darker());
                g2.setStroke(new BasicStroke(2f));
                g2.drawArc(centerX - radius, centerY - radius, radius * 2, radius * 2,
                        (int) startAngle, (int) Math.ceil(usAngle));
                startAngle += usAngle;
            }

            // CRYPTO
            if (cryptoValue > 0) {
                g2.setColor(COLOR_CRYPTO);
                g2.fillArc(centerX - radius, centerY - radius, radius * 2, radius * 2,
                        (int) startAngle, (int) Math.ceil(cryptoAngle));
                g2.setColor(COLOR_CRYPTO.darker());
                g2.setStroke(new BasicStroke(2f));
                g2.drawArc(centerX - radius, centerY - radius, radius * 2, radius * 2,
                        (int) startAngle, (int) Math.ceil(cryptoAngle));
                startAngle += cryptoAngle;
            }

            // COMMODITY
            if (commodityValue > 0) {
                g2.setColor(COLOR_COMMODITY);
                g2.fillArc(centerX - radius, centerY - radius, radius * 2, radius * 2,
                        (int) startAngle, (int) Math.ceil(commodityAngle));
                g2.setColor(COLOR_COMMODITY.darker());
                g2.setStroke(new BasicStroke(2f));
                g2.drawArc(centerX - radius, centerY - radius, radius * 2, radius * 2,
                        (int) startAngle, (int) Math.ceil(commodityAngle));
            }

            // İç boşluğu temizle (halka efekti)
            g2.setColor(getPANEL_BG());
            g2.fillOval(centerX - innerRadius, centerY - innerRadius, innerRadius * 2, innerRadius * 2);
            g2.setColor(getBORDER());
            g2.setStroke(new BasicStroke(1f));
            g2.drawOval(centerX - innerRadius, centerY - innerRadius, innerRadius * 2, innerRadius * 2);

            // Ortadaki yüzde bilgisi - gizleme durumuna göre
            if (portfolioValueVisible) {
                g2.setColor(getTEXT());
                g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
                String totalText = PRICE_FMT.format(total);
                FontMetrics fm = g2.getFontMetrics();
                int textWidth = fm.stringWidth(totalText);
                int textHeight = fm.getHeight();
                g2.drawString(totalText, centerX - textWidth / 2, centerY + textHeight / 4);

                g2.setColor(getTEXT_MUTED());
                g2.setFont(FONT_REGULAR);
                String labelText = "Toplam";
                fm = g2.getFontMetrics();
                textWidth = fm.stringWidth(labelText);
                g2.drawString(labelText, centerX - textWidth / 2, centerY - textHeight / 2);
            } else {
                // Gizli durumda "••••••" göster
                g2.setColor(getTEXT());
                g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
                String hiddenText = "••••••";
                FontMetrics fm = g2.getFontMetrics();
                int textWidth = fm.stringWidth(hiddenText);
                int textHeight = fm.getHeight();
                g2.drawString(hiddenText, centerX - textWidth / 2, centerY + textHeight / 4);

                g2.setColor(getTEXT_MUTED());
                g2.setFont(FONT_REGULAR);
                String labelText = "Toplam";
                fm = g2.getFontMetrics();
                textWidth = fm.stringWidth(labelText);
                g2.drawString(labelText, centerX - textWidth / 2, centerY - textHeight / 2);
            }

            // Legend (gösterge) - Sağ tarafta daha görünür
            drawLegend(g2, centerX + radius + 20, centerY - 40);
            g2.dispose();
        }

        private void drawLegend(Graphics2D g2, int x, int y) {
            int boxSize = 14; // Biraz daha büyük kutu
            int lineHeight = 20; // Daha fazla boşluk
            Font legendFont = new Font("Segoe UI", Font.BOLD, 12); // Daha büyük ve kalın font

            g2.setFont(legendFont);
            int currentY = y;

            double total = bistValue + usValue + cryptoValue + commodityValue;
            if (total == 0) return;

            g2.setFont(legendFont);

            // BIST
            if (bistValue > 0) {
                g2.setColor(COLOR_BIST);
                g2.fillRect(x, currentY, boxSize, boxSize);
                g2.setColor(COLOR_BIST.darker());
                g2.setStroke(new BasicStroke(1f));
                g2.drawRect(x, currentY, boxSize, boxSize);
                g2.setColor(getTEXT());
                double pct = (bistValue / total) * 100;
                g2.drawString("BIST: " + String.format("%.1f%%", pct), x + boxSize + 8, currentY + boxSize - 2);
                currentY += lineHeight;
            }

            // US
            if (usValue > 0) {
                g2.setColor(COLOR_US);
                g2.fillRect(x, currentY, boxSize, boxSize);
                g2.setColor(COLOR_US.darker());
                g2.setStroke(new BasicStroke(1f));
                g2.drawRect(x, currentY, boxSize, boxSize);
                g2.setColor(getTEXT());
                double pct = (usValue / total) * 100;
                g2.drawString("US: " + String.format("%.1f%%", pct), x + boxSize + 8, currentY + boxSize - 2);
                currentY += lineHeight;
            }

            // CRYPTO
            if (cryptoValue > 0) {
                g2.setColor(COLOR_CRYPTO);
                g2.fillRect(x, currentY, boxSize, boxSize);
                g2.setColor(COLOR_CRYPTO.darker());
                g2.setStroke(new BasicStroke(1f));
                g2.drawRect(x, currentY, boxSize, boxSize);
                g2.setColor(getTEXT());
                double pct = (cryptoValue / total) * 100;
                g2.drawString("CRYPTO: " + String.format("%.1f%%", pct), x + boxSize + 8, currentY + boxSize - 2);
                currentY += lineHeight;
            }

            // COMMODITY
            if (commodityValue > 0) {
                g2.setColor(COLOR_COMMODITY);
                g2.fillRect(x, currentY, boxSize, boxSize);
                g2.setColor(COLOR_COMMODITY.darker());
                g2.setStroke(new BasicStroke(1f));
                g2.drawRect(x, currentY, boxSize, boxSize);
                g2.setColor(getTEXT());
                double pct = (commodityValue / total) * 100;
                g2.drawString("EMTİA: " + String.format("%.1f%%", pct), x + boxSize + 8, currentY + boxSize - 2);
            }
        }
    }

    /**
     * Portföy değer değişim grafiğini günceller
     */
    private void updateLineChart() {
        if (lineChartPanel == null) return;

        PortfolioValueDao portfolioValueDao = new PortfolioValueDao();
        // Son 30 günü al - günün başlangıcından itibaren (saat bilgisi olmadan)
        // Bugünün başlangıcından 30 gün öncesinin başlangıcına kadar
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate thirtyDaysAgoDate = today.minusDays(30);
        java.time.LocalDateTime thirtyDaysAgo = thirtyDaysAgoDate.atStartOfDay();
        
        // Son 30 gün içindeki tüm kayıtları al (limit yok)
        List<PortfolioValueDao.PortfolioValue> values = portfolioValueDao.listByUser(user.getId(), thirtyDaysAgo, null);
        
        // Eğer 30 günde yeterli veri yoksa, son 500 kaydı al (daha fazla veri için)
        if (values.size() < 2) {
            values = portfolioValueDao.getLastKRecords(user.getId(), 500);
        }

        lineChartPanel.updateData(values);
    }

    /**
     * Portföy değer değişimini gösteren line chart paneli
     */
    private class PortfolioLineChartPanel extends JPanel {
        private XChartPanel<XYChart> chartPanel;
        private XYChart chart;

        public PortfolioLineChartPanel() {
            setOpaque(false);
            setLayout(new BorderLayout());
            
            // Boş durum için başlangıç grafiği
            createEmptyChart();
        }

        private void createEmptyChart() {
            chart = new XYChartBuilder()
                    .width(600)
                    .height(180)
                    .title("")
                    .xAxisTitle("Tarih")
                    .yAxisTitle("Değer (₺)")
                    .build();

            // Stil ayarları
            applyChartTheme();

            chartPanel = new XChartPanel<>(chart);
            chartPanel.setOpaque(false);
            
            removeAll();
            add(chartPanel, BorderLayout.CENTER);
        }

        public void updateData(List<PortfolioValueDao.PortfolioValue> values) {
            if (values == null || values.isEmpty()) {
                showEmptyState();
                return;
            }

            // Verileri günlük tek kayıt olacak şekilde grupla (her gün için son kayıt alınır)
            java.util.Map<java.time.LocalDate, PortfolioValueDao.PortfolioValue> dailyData = new java.util.LinkedHashMap<>();
            for (PortfolioValueDao.PortfolioValue pv : values) {
                LocalDateTime ldt = pv.calculatedAt();
                java.time.LocalDate date = ldt.toLocalDate();
                // Eğer bu tarih için kayıt yoksa veya daha yeni bir kayıt varsa güncelle
                if (!dailyData.containsKey(date) || 
                    pv.calculatedAt().isAfter(dailyData.get(date).calculatedAt())) {
                    dailyData.put(date, pv);
                }
            }

            // Verileri hazırla (tarihe göre sıralı)
            List<Date> dates = new ArrayList<>();
            List<Double> portfolioValues = new ArrayList<>();
            
            // Tarihleri sırala
            List<java.time.LocalDate> sortedDates = new ArrayList<>(dailyData.keySet());
            sortedDates.sort(java.util.Comparator.naturalOrder());
            
            for (java.time.LocalDate localDate : sortedDates) {
                PortfolioValueDao.PortfolioValue pv = dailyData.get(localDate);
                // Günün başlangıcına ayarla (saat bilgisi olmasın)
                LocalDateTime dayStart = localDate.atStartOfDay();
                Date date = Date.from(dayStart.atZone(ZoneId.systemDefault()).toInstant());
                dates.add(date);
                portfolioValues.add(pv.totalValue());
            }

            // Grafiği yeniden oluştur (tema kullanmadan, manuel renk ayarları yapacağız)
            chart = new XYChartBuilder()
                    .width(600)
                    .height(180)
                    .title("")
                    .xAxisTitle("Tarih")
                    .yAxisTitle("Değer (₺)")
                    .build();

            // Stil ayarları
            applyChartTheme();

            // X ekseni tarih formatı - sadece tarih (saat yok)
            chart.getStyler().setDatePattern("dd.MM.yyyy");
            chart.getStyler().setXAxisLabelRotation(45);
            // X ekseni etiketlerini seyrekleştir
            chart.getStyler().setXAxisTickMarkSpacingHint(80);
            // X ekseni etiket sayısını sınırla
            if (dates.size() > 10) {
                chart.getStyler().setXAxisTickMarkSpacingHint(100);
            }

            // Y ekseni formatı: TL formatında, binlik ayırıcı, 2 ondalık
            chart.getStyler().setYAxisDecimalPattern("#,##0.00");
            // Y eksenine padding ekle (min ve max değerlerin üstüne/altına boşluk)
            if (!portfolioValues.isEmpty()) {
                double minValue = portfolioValues.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                double maxValue = portfolioValues.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                double range = maxValue - minValue;
                if (range > 0) {
                    double padding = range * 0.1; // %10 padding
                    chart.getStyler().setYAxisMin(minValue - padding);
                    chart.getStyler().setYAxisMax(maxValue + padding);
                }
            }

            // Veri serisini ekle
            org.knowm.xchart.XYSeries series = chart.addSeries("Portföy Değeri", dates, portfolioValues);
            series.setLineColor(new Color(66, 153, 225)); // Mavi çizgi
            series.setLineWidth(2.5f);

            // Tooltip ekle - XChart otomatik tooltip desteği var
            chart.getStyler().setToolTipsEnabled(true);

            // Chart panel'i güncelle
            if (chartPanel != null) {
                remove(chartPanel);
            }
            chartPanel = new XChartPanel<>(chart);
            chartPanel.setOpaque(false);
            
            removeAll();
            add(chartPanel, BorderLayout.CENTER);
            revalidate();
            repaint();
        }
        
        /**
         * Chart tema ayarlarını uygular - her iki temada da aynı renkler
         */
        private void applyChartTheme() {
            if (chart == null) return;
            
            // Sabit renkler - her iki temada da aynı kalacak
            Color chartBg = Color.WHITE; // Beyaz arka plan
            Color textColor = new Color(33, 37, 41); // Koyu gri metin
            Color borderColor = new Color(220, 224, 230); // Açık gri border ve grid
            
            // Arka plan renkleri - her zaman beyaz
            chart.getStyler().setChartBackgroundColor(chartBg);
            chart.getStyler().setPlotBackgroundColor(chartBg);
            chart.getStyler().setPlotBorderColor(borderColor);
            
            // Metin renkleri - her zaman koyu gri
            chart.getStyler().setChartFontColor(textColor);
            
            // Grid çizgileri - her zaman açık gri
            chart.getStyler().setPlotGridLinesVisible(true);
            chart.getStyler().setPlotGridLinesColor(borderColor);
            
            // Eksen çizgileri - her zaman açık gri
            chart.getStyler().setAxisTickMarksColor(borderColor);
            chart.getStyler().setAxisTickMarkLength(5);
            
            // Diğer ayarlar
            chart.getStyler().setLegendVisible(false);
            chart.getStyler().setAxisTitlesVisible(true);
            chart.getStyler().setAxisTitleFont(new Font("Segoe UI", Font.PLAIN, 11));
            chart.getStyler().setAxisTickLabelsFont(new Font("Segoe UI", Font.PLAIN, 10));
            chart.getStyler().setXAxisTickMarkSpacingHint(50);
            chart.getStyler().setYAxisTickMarkSpacingHint(30);
        }
        

        private void showEmptyState() {
            removeAll();
            
            JLabel emptyLabel = new JLabel("Henüz yeterli veri yok");
            emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
            emptyLabel.setVerticalAlignment(SwingConstants.CENTER);
            emptyLabel.setForeground(getTEXT_MUTED());
            emptyLabel.setFont(FONT_REGULAR);
            
            add(emptyLabel, BorderLayout.CENTER);
            revalidate();
            repaint();
        }
    }
    
    /**
     * Fiyatları günceller (hem manuel hem otomatik güncelleme için)
     * @param isAutoUpdate true ise otomatik güncelleme, false ise manuel güncelleme
     * @param btnRefresh Yenile butonu (manuel güncelleme için gerekli, otomatik güncellemede null olabilir)
     */
    private void refreshPrices(boolean isAutoUpdate, JButton btnRefresh) {
        // Portföydeki varlıkların asset ID'lerini topla
        dao.PortfolioDao portfolioDao = new dao.PortfolioDao();
        var portfolioRows = portfolioDao.getPortfolioSummary(user.getId());
        java.util.Set<Integer> assetIds = portfolioRows.stream()
                .map(row -> row.assetId)
                .collect(java.util.stream.Collectors.toSet());
        
        // Eğer portföy boşsa işlem yapma
        if (assetIds.isEmpty()) {
            return;
        }
        
        // Manuel güncelleme için butonu devre dışı bırak
        if (!isAutoUpdate && btnRefresh != null) {
            btnRefresh.setEnabled(false);
            btnRefresh.setToolTipText("Fiyatlar güncelleniyor...");
        }
        
        // Arka planda sadece portföydeki varlıkların fiyatlarını çek ve kaydet
        SwingWorker<int[], Void> worker = new SwingWorker<int[], Void>() {
            private Exception error = null;
            
            @Override
            protected int[] doInBackground() throws Exception {
                try {
                    // Sadece portföydeki varlıkların fiyatlarını çek
                    return PriceHistorySeeder.fetchAndSavePricesForAssets(assetIds);
                } catch (Exception ex) {
                    error = ex;
                    throw ex;
                }
            }
            
            @Override
            protected void done() {
                // Manuel güncelleme için butonu tekrar etkinleştir
                if (!isAutoUpdate && btnRefresh != null) {
                    btnRefresh.setEnabled(true);
                    btnRefresh.setToolTipText("Yenile");
                }
                
                try {
                    int[] results = get();
                    int ok = results[0];
                    int fail = results[1];
                    
                    // Tüm yenileme işlemlerini yap:
                    // 1. Portföy tablosunu yeniden yükle (fiyatlar, kar/zarar)
                    loadPortfolioFromDb();
                    
                    // 2. Toplam portföy değerini ve kar/zarar değerini yenile
                    refreshPortfolioValues();
                    
                    // 3. Portföy değeri değişim grafiğini yenile
                    updateLineChart();
                    
                    // Bildirim göster - sadece manuel güncellemede
                    if (!isAutoUpdate) {
                        // Manuel güncelleme: bildirim göster
                        if (fail == 0) {
                            showInfoDialog("Fiyatlar başarıyla güncellendi. (" + ok + " varlık)");
                        } else {
                            showInfoDialog("Fiyatlar güncellendi. Başarılı: " + ok + ", Başarısız: " + fail);
                        }
                    }
                    // Otomatik güncellemede bildirim gösterilmez (sessiz güncelleme)
                } catch (Exception ex) {
                    ex.printStackTrace();
                    String errorMsg = error != null ? error.getMessage() : ex.getMessage();
                    // Hata durumunda sadece manuel güncellemede bildirim göster
                    if (!isAutoUpdate) {
                        showErrorDialog("Fiyat güncelleme hatası: " + errorMsg);
                    }
                }
            }
        };
        worker.execute();
    }
    
    /**
     * Otomatik fiyat güncelleme timer'ını başlatır (1 dakikada bir)
     */
    private void startAutoRefreshTimer() {
        // 1 dakika = 60000 milisaniye
        autoRefreshTimer = new javax.swing.Timer(60000, e -> {
            refreshPrices(true, null); // Otomatik güncelleme
        });
        autoRefreshTimer.setRepeats(true); // Sürekli tekrarla
        autoRefreshTimer.start(); // Timer'ı başlat
    }
}
