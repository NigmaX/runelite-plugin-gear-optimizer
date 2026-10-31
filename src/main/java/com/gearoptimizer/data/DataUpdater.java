package com.gearoptimizer.data;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Downloads a fresh copy of the OSRS Wiki DPS calculator dataset, on request only.
 * Two requests per refresh; the result is stored under .runelite/gear-optimizer.
 */
@Slf4j
@Singleton
public class DataUpdater
{
	private static final String BASE_URL = "https://raw.githubusercontent.com/weirdgloop/osrs-dps-calc/main/cdn/json/";

	private final OkHttpClient okHttpClient;
	private final GameDataRepository repository;

	@Inject
	DataUpdater(OkHttpClient okHttpClient, GameDataRepository repository)
	{
		this.okHttpClient = okHttpClient;
		this.repository = repository;
	}

	/**
	 * @param loader runs the reload once the files are saved, so it doesn't race a calculation
	 * @param onDone called with null on success, or an error message; not on the EDT
	 */
	public void refresh(Executor loader, Consumer<String> onDone)
	{
		CompletableFuture<String> monsters = fetch(GameDataRepository.MONSTERS_FILE);
		CompletableFuture<String> equipment = fetch(GameDataRepository.EQUIPMENT_FILE);

		CompletableFuture.allOf(monsters, equipment).whenComplete((ignored, error) ->
		{
			if (error != null)
			{
				log.debug("Gear optimizer data download failed", error);
				onDone.accept("Download failed: " + rootMessage(error));
				return;
			}

			String monstersJson = monsters.join();
			String equipmentJson = equipment.join();
			if (!repository.validate(monstersJson, equipmentJson))
			{
				onDone.accept("Downloaded data was not valid; kept the current data");
				return;
			}

			try
			{
				write(GameDataRepository.MONSTERS_FILE, monstersJson);
				write(GameDataRepository.EQUIPMENT_FILE, equipmentJson);
				loader.execute(() ->
				{
					repository.load();
					onDone.accept(null);
				});
			}
			catch (IOException e)
			{
				log.warn("Unable to save gear optimizer data", e);
				onDone.accept("Could not save data: " + e.getMessage());
			}
		});
	}

	/**
	 * Removes downloaded data so the bundled copy is used again.
	 */
	public void resetToBundled()
	{
		new File(GameDataRepository.DATA_DIR, GameDataRepository.MONSTERS_FILE).delete();
		new File(GameDataRepository.DATA_DIR, GameDataRepository.EQUIPMENT_FILE).delete();
		repository.load();
	}

	private CompletableFuture<String> fetch(String file)
	{
		CompletableFuture<String> future = new CompletableFuture<>();
		Request request = new Request.Builder().url(BASE_URL + file).build();
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				future.completeExceptionally(e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						future.completeExceptionally(new IOException("HTTP " + response.code() + " for " + file));
						return;
					}
					future.complete(body.string());
				}
				catch (IOException e)
				{
					future.completeExceptionally(e);
				}
			}
		});
		return future;
	}

	private static void write(String name, String contents) throws IOException
	{
		File dir = GameDataRepository.DATA_DIR;
		if (!dir.isDirectory() && !dir.mkdirs())
		{
			throw new IOException("Cannot create " + dir);
		}
		File tmp = new File(dir, name + ".tmp");
		Files.write(tmp.toPath(), contents.getBytes(StandardCharsets.UTF_8));
		Files.move(tmp.toPath(), new File(dir, name).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	private static String rootMessage(Throwable t)
	{
		while (t.getCause() != null)
		{
			t = t.getCause();
		}
		return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
	}
}
