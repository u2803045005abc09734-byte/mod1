package de.sunpacks.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_10799;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_4185;
import net.minecraft.class_437;

/** Modrinth-Pack-Browser mit großen Projektkarten, Icons, Sortierung und Seiten-Navigation. */
public class PackBrowserScreen extends SunScreen {
	private static final String MC_VERSION = "1.21.11";
	private static final int PAGE_SIZE = 5;
	private static final int CARD_HEIGHT = 60;
	private static final int CARD_GAP = 64;
	private static final String[] SORTS = {"relevance", "downloads", "updated", "newest"};
	private static final String[] SORT_NAMES = {"Relevanz", "Downloads", "Aktualisiert", "Neueste"};
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(10))
		.build();

	private record Hit(String id, String title, String author, String description, long downloads, String iconUrl) {}
	private record IconInfo(class_2960 texture, int width, int height) {}
	private static final Map<String, IconInfo> ICONS = new ConcurrentHashMap<>();
	private static final Set<String> ICONS_REQUESTED = ConcurrentHashMap.newKeySet();
	private static String lastQuery = "";

	private class_342 searchBox;
	private final List<class_4185> resultButtons = new ArrayList<>();
	private class_4185 previousButton;
	private class_4185 nextButton;
	private class_4185 sortButton;
	private volatile List<Hit> hits = List.of();
	private volatile int totalHits = 0;
	private volatile int version = 0;
	private volatile boolean searching = false;
	private int shownVersion = -1;
	private int page = 0;
	private int sortIndex = 1;
	private volatile String status = "Search Modrinth resource packs and install them in-game.";

	public PackBrowserScreen(class_437 parent) {
		super("Modrinth Resource Packs", parent);
	}

	@Override
	protected void method_25426() {
		resultButtons.clear();
		int center = this.field_22789 / 2;
		int left = center - 180;

		searchBox = new class_342(this.field_22793, left, 38, 175, 20,
			class_2561.method_43470("Resource Pack suchen"));
		searchBox.method_1852(lastQuery);
		method_37063(searchBox);
		addButton("Suchen", center, 38, 65, () -> search(searchBox.method_1882()));
		sortButton = addButton(sortLabel(), center + 70, 38, 110, () -> {
			sortIndex = (sortIndex + 1) % SORTS.length;
			sortButton.method_25355(class_2561.method_43470(sortLabel()));
			page = 0;
			searchCurrentPage();
		});

		for (int i = 0; i < PAGE_SIZE; i++) {
			final int index = i;
			class_4185 button = method_37063(class_4185.method_46430(class_2561.method_43470(""), b -> {
				List<Hit> current = hits;
				if (!searching && index < current.size()) download(current.get(index));
			}).method_46434(left, 70 + i * CARD_GAP, 360, CARD_HEIGHT).method_46431());
			button.field_22764 = false;
			resultButtons.add(button);
		}

		int navY = this.field_22790 - 66;
		previousButton = addButton("‹ Zurück", left, navY, 76, () -> {
			if (page > 0 && !searching) { page--; searchCurrentPage(); }
		});
		nextButton = addButton("Weiter ›", left + 82, navY, 76, () -> {
			if ((page + 1) * PAGE_SIZE < totalHits && !searching) { page++; searchCurrentPage(); }
		});
		addButton("Schließen", left + 260, navY, 100, this::method_25419);

		shownVersion = -1;
		search(lastQuery);
	}

	private String sortLabel() {
		return "Sort: " + SORT_NAMES[sortIndex];
	}

	private void search(String query) {
		lastQuery = query == null ? "" : query.trim();
		page = 0;
		searchCurrentPage();
	}

	private void searchCurrentPage() {
		final String query = lastQuery;
		final int offset = page * PAGE_SIZE;
		final String sort = SORTS[sortIndex];
		searching = true;
		hits = List.of();
		totalHits = 0;
		status = "Suche auf Modrinth …";
		version++;

		CompletableFuture.runAsync(() -> {
			try {
				String facets = "[[\"project_type:resourcepack\"],[\"versions:" + MC_VERSION + "\"]]";
				String url = "https://api.modrinth.com/v2/search?query="
					+ URLEncoder.encode(query, StandardCharsets.UTF_8)
					+ "&facets=" + URLEncoder.encode(facets, StandardCharsets.UTF_8)
					+ "&limit=" + PAGE_SIZE
					+ "&offset=" + offset
					+ "&index=" + sort;
				JsonObject root = JsonParser.parseString(get(url)).getAsJsonObject();
				JsonArray arr = root.getAsJsonArray("hits");
				List<Hit> result = new ArrayList<>();
				for (JsonElement el : arr) {
					JsonObject o = el.getAsJsonObject();
					String icon = o.has("icon_url") && !o.get("icon_url").isJsonNull()
						? o.get("icon_url").getAsString() : "";
					Hit hit = new Hit(
						o.get("project_id").getAsString(),
						o.get("title").getAsString(),
						o.has("author") ? o.get("author").getAsString() : "Unbekannt",
						o.has("description") ? o.get("description").getAsString() : "",
						o.has("downloads") ? o.get("downloads").getAsLong() : 0L,
						icon);
					result.add(hit);
					loadIcon(hit);
				}
				hits = result;
				totalHits = root.has("total") ? root.get("total").getAsInt() : result.size();
				status = searchingMessage(result.size(), totalHits);
			} catch (Exception e) {
				hits = List.of();
				totalHits = 0;
				status = "Suche fehlgeschlagen: " + safeError(e);
			} finally {
				searching = false;
				version++;
			}
		});
	}

	private String searchingMessage(int shown, int total) {
		if (total == 0 || shown == 0) return "Keine Resource Packs für Minecraft " + MC_VERSION + " gefunden.";
		return total + " Resource Packs gefunden · Karte anklicken zum Installieren.";
	}

	private static String formatNumber(long n) {
		if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
		if (n >= 1_000) return String.format(Locale.ROOT, "%.1fk", n / 1_000.0);
		return String.valueOf(n);
	}

	private static String shortText(String text, int maxChars) {
		if (text == null) return "";
		String oneLine = text.replace('\n', ' ').replace('\r', ' ').trim();
		return oneLine.length() <= maxChars ? oneLine : oneLine.substring(0, Math.max(0, maxChars - 1)) + "…";
	}

	private void loadIcon(Hit hit) {
		if (hit.iconUrl().isBlank() || ICONS.containsKey(hit.id()) || !ICONS_REQUESTED.add(hit.id())) return;
		CompletableFuture.runAsync(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(hit.iconUrl()))
					.header("User-Agent", "SunPackBrowser/1.0.0 (Fabric mod)")
					.timeout(Duration.ofSeconds(8)).GET().build();
				HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
				byte[] bytes = response.body();
				if (response.statusCode() != 200 || bytes.length == 0 || bytes.length > 1_000_000) return;
				class_310 mc = class_310.method_1551();
				mc.execute(() -> {
					class_1011 image = null;
					try {
						image = class_1011.method_4309(new ByteArrayInputStream(bytes));
						int width = image.method_4307();
						int height = image.method_4323();
						if (width <= 0 || height <= 0 || width > 512 || height > 512) {
							image.close();
							return;
						}
						String textureName = "modrinth_" + hit.id().toLowerCase(Locale.ROOT)
							.replaceAll("[^a-z0-9_/-]", "_");
						class_1043 texture = new class_1043(() -> textureName, image);
						class_2960 id = class_2960.method_60655("sunpacks", textureName);
						mc.method_1531().method_4616(id, texture);
						ICONS.put(hit.id(), new IconInfo(id, width, height));
					} catch (Exception ignored) {
						if (image != null) image.close();
					}
				});
			} catch (Exception ignored) {
				// Fehlende Projekt-Icons sollen die Packliste nicht blockieren.
			}
		});
	}

	private void download(Hit hit) {
		status = "Lade „" + hit.title() + "“ …";
		CompletableFuture.runAsync(() -> {
			try {
				String gv = URLEncoder.encode("[\"" + MC_VERSION + "\"]", StandardCharsets.UTF_8);
				String body = get("https://api.modrinth.com/v2/project/" + hit.id() + "/version?game_versions=" + gv);
				JsonArray versions = JsonParser.parseString(body).getAsJsonArray();
				if (versions.isEmpty()) {
					status = "Keine Version für Minecraft " + MC_VERSION + " gefunden.";
					return;
				}
				JsonArray files = versions.get(0).getAsJsonObject().getAsJsonArray("files");
				JsonObject chosen = null;
				for (JsonElement el : files) {
					JsonObject f = el.getAsJsonObject();
					if (chosen == null) chosen = f;
					if (f.has("primary") && f.get("primary").getAsBoolean()) { chosen = f; break; }
				}
				if (chosen == null) { status = "Keine Datei für dieses Pack gefunden."; return; }
				String fileUrl = chosen.get("url").getAsString();
				String fileName = chosen.get("filename").getAsString().replaceAll("[\\\\/:*?\"<>|]", "_");
				Path dir = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
				Files.createDirectories(dir);
				Path target = dir.resolve(fileName).normalize();
				if (!target.getParent().equals(dir.normalize())) throw new IllegalStateException("Ungültiger Dateiname vom Download.");
				Path tmp = dir.resolve(fileName + ".part");
				HttpRequest req = HttpRequest.newBuilder(URI.create(fileUrl))
					.header("User-Agent", "SunPackBrowser/1.0.0 (Fabric mod)")
					.timeout(Duration.ofMinutes(5)).GET().build();
				HttpResponse<Path> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofFile(tmp));
				if (resp.statusCode() != 200) {
					Files.deleteIfExists(tmp);
					status = "Download fehlgeschlagen (HTTP " + resp.statusCode() + ").";
					return;
				}
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
				status = "Installiert: " + fileName + " · In den Ressourcenpaket-Einstellungen aktivieren.";
				class_310 mc = class_310.method_1551();
				mc.execute(() -> mc.method_1520().method_14445());
			} catch (Exception e) {
				status = "Download fehlgeschlagen: " + safeError(e);
			}
		});
	}

	private static String safeError(Exception e) {
		String message = e.getMessage();
		if (message == null || message.isBlank()) return e.getClass().getSimpleName();
		return shortText(message.replace('\n', ' '), 90);
	}

	private static String get(String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
			.header("User-Agent", "SunPackBrowser/1.0.0 (Fabric mod)")
			.timeout(Duration.ofSeconds(15)).GET().build();
		HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode());
		return resp.body();
	}

	@Override
	public void method_25394(class_332 g, int mouseX, int mouseY, float delta) {
		if (shownVersion != version) {
			List<Hit> current = hits;
			for (int i = 0; i < resultButtons.size(); i++) {
				class_4185 button = resultButtons.get(i);
				button.field_22764 = !searching && i < current.size();
				button.method_25355(class_2561.method_43470(""));
			}
			shownVersion = version;
		}
		previousButton.field_22764 = !searching && page > 0;
		nextButton.field_22764 = !searching && (page + 1) * PAGE_SIZE < totalHits;
		sortButton.field_22764 = !searching;
		super.method_25394(g, mouseX, mouseY, delta);

		int center = this.field_22789 / 2;
		int left = center - 180;
		List<Hit> current = hits;
		for (int i = 0; i < Math.min(current.size(), PAGE_SIZE); i++) {
			Hit hit = current.get(i);
			int x = left;
			int y = 70 + i * CARD_GAP;
			boolean hovered = mouseX >= x && mouseX < x + 360 && mouseY >= y && mouseY < y + CARD_HEIGHT;
			g.method_25294(x, y, x + 360, y + CARD_HEIGHT, hovered ? 0xF02D394B : 0xE61A202C);
			g.method_25294(x, y, x + 3, y + CARD_HEIGHT, 0xFFFFC107);
			g.method_25294(x + 7, y + 6, x + 55, y + 54, 0xFF30394A);
			IconInfo icon = ICONS.get(hit.id());
			if (icon != null) {
				g.method_25302(class_10799.field_56883, icon.texture(), x + 7, y + 6,
					0.0f, 0.0f, 48, 48, icon.width(), icon.height(), icon.width(), icon.height());
			} else {
				g.method_27534(this.field_22793, class_2561.method_43470("SUN"), x + 31, y + 23, 0xFFFFC107);
			}
			g.method_51433(this.field_22793, shortText(hit.title(), 43), x + 63, y + 7, 0xFFFFFFFF, true);
			String meta = shortText(hit.author(), 24) + "  ·  " + formatNumber(hit.downloads()) + " Downloads";
			g.method_51433(this.field_22793, shortText(meta, 42), x + 63, y + 22, 0xFFFFC107, false);
			g.method_51433(this.field_22793, shortText(hit.description(), 45), x + 63, y + 38, 0xFFB8C1D1, false);
		}

		int totalPages = Math.max(1, (totalHits + PAGE_SIZE - 1) / PAGE_SIZE);
		String pageText = searching ? "Lädt …" : "Seite " + (page + 1) + " / " + totalPages;
		g.method_27534(this.field_22793, class_2561.method_43470(pageText), left + 215, this.field_22790 - 60, 0xFFFFFFFF);
		g.method_27534(this.field_22793, class_2561.method_43470(shortText(status, 90)), center, this.field_22790 - 38, 0xFFE3E8F0);
	}
}
