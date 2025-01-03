package ch.njol.skript.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

import org.skriptlang.skript.lang.converter.Converter;


/**
 * @author Peter Güttinger
 */
public abstract class FileUtils {

	private static boolean RUNNINGJAVA6 = true;// = System.getProperty("java.version").startsWith("1.6"); // doesn't work reliably?
	static {
		try {
			new File(".").toPath();
			RUNNINGJAVA6 = false;
		} catch (final NoSuchMethodError e) {
			RUNNINGJAVA6 = true;
		} catch (final Exception e) {
			RUNNINGJAVA6 = false;
		}
	}

	private FileUtils() {}

	private final static SimpleDateFormat backupFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

	/**
	 * @return The current date and time
	 */
	public static String getBackupSuffix() {
		synchronized (backupFormat) {
			return "" + backupFormat.format(System.currentTimeMillis());
		}
	}

	/**
	 * Deletes files in backup directory to meet desired target, starting from oldest to newest
	 *
	 * @param csvFile Variable file in order to get 'backups' directory
	 * @param toKeep Integer of how many files are to be left remaining
	 * @throws IOException If 'backups' directory is not found
	 * @throws IllegalArgumentException If 'toKeep' parameter is less than 0
	 */
	public static void backupPurge(File varFile, int toKeep) throws IOException, IllegalArgumentException {
		if (toKeep < 0)
			throw new IllegalArgumentException("Called with invalid input, 'toKeep' can not be less than 0");
		File backupDir = new File(varFile.getParentFile(), "backups" + File.separator);
		if (!backupDir.exists() || !backupDir.isDirectory())
			throw new IOException("Backup directory not found");
		ArrayList<File> files = new ArrayList<File>(Arrays.asList(backupDir.listFiles()));
		if (files == null || files.size() <= toKeep)
			return;
		if (toKeep > 0)
			files.sort(Comparator.comparingLong(File::lastModified));
		int numberToRemove = files.size() - toKeep;
		for (int i = 0; i < numberToRemove; i++) {
			files.get(i).delete();
		}
	}

	public static File backup(final File f) throws IOException {
		String name = f.getName();
		final int c = name.lastIndexOf('.');
		final String ext = c == -1 ? null : name.substring(c + 1);
		if (c != -1)
			name = name.substring(0, c);
		final File backupFolder = new File(f.getParentFile(), "backups" + File.separator);
		if (!backupFolder.exists() && !backupFolder.mkdirs())
			throw new IOException("Cannot create backups folder");
		final File backup = new File(backupFolder, name + "_" + getBackupSuffix() + (ext == null ? "" : "." + ext));
		if (backup.exists())
			throw new IOException("Backup file " + backup.getName() + " does already exist");
		copy(f, backup);
		return backup;
	}

	public static File move(final File from, final File to, final boolean replace) throws IOException {
		if (!replace && to.exists())
			throw new IOException("Can't rename " + from.getName() + " to " + to.getName() + ": The target file already exists");
		if (!RUNNINGJAVA6) {
			if (replace)
				Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			else
				Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE);
		} else {
			File moveTo = null;
			if (replace && to.exists()) {
				moveTo = new File(to.getAbsolutePath() + ".old0");
				int i = 0;
				while (moveTo.exists() && i < 1000)
					moveTo = new File(to.getAbsolutePath() + ".old" + (++i));
				if (i == 999 || !to.renameTo(moveTo))
					throw new IOException("Can't rename " + from.getName() + " to " + to.getName() + ": Cannot temporarily rename the target file");
			}
			if (!from.renameTo(to)) {
				if (moveTo != null)
					moveTo.renameTo(to);
				throw new IOException("Can't rename " + from.getName() + " to " + to.getName());
			}
			if (moveTo != null)
				moveTo.delete();
		}
		return to;
	}

	public static void copy(final File from, final File to) throws IOException {
		if (!RUNNINGJAVA6) {
			Files.copy(from.toPath(), to.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
		} else {
			FileInputStream in = null;
			FileOutputStream out = null;
			try {
				in = new FileInputStream(from);
				out = new FileOutputStream(to);
				final byte[] buffer = new byte[4096];
				int bytesRead;
				while ((bytesRead = in.read(buffer)) != -1)
					out.write(buffer, 0, bytesRead);
			} catch (final Exception e) {
				throw new IOException("Can't copy " + from.getName() + " to " + to.getName() + ": " + e.getLocalizedMessage(), e);
			} finally {
				if (in != null) {
					try {
						in.close();
					} catch (final IOException e) {}
				}
				if (out != null) {
					try {
						out.close();
					} catch (final IOException e) {}
				}
			}
		}
	}

	/**
	 * @param directory
	 * @param renamer Renames files. Return null to leave a file as-is.
	 * @return A collection of all changed files (with their new names)
	 * @throws IOException If renaming one of the files caused an IOException. Some files might have been renamed already.
	 */
	public static Collection<File> renameAll(final File directory, final Converter<String, String> renamer) throws IOException {
		final Collection<File> changed = new ArrayList<>();
		for (final File f : directory.listFiles()) {
			if (f.isDirectory()) {
				changed.addAll(renameAll(f, renamer));
			} else {
				final String name = f.getName();
				if (name == null)
					continue;
				final String newName = renamer.convert(name);
				if (newName == null)
					continue;
				final File newFile = new File(f.getParent(), newName);
				move(f, newFile, false);
				changed.add(newFile);
			}
		}
		return changed;
	}

	/**
	 * Saves the contents of an InputStream in a file.
	 *
	 * @param in The InputStream to read from. This stream will not be closed when this method returns.
	 * @param file The file to save to. Will be replaced if it exists, or created if it doesn't.
	 * @throws IOException
	 */
	public static void save(final InputStream in, final File file) throws IOException {
		file.getParentFile().mkdirs();
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(file);
			final byte[] buffer = new byte[16 * 1024];
			int read;
			while ((read = in.read(buffer)) > 0) {
				out.write(buffer, 0, read);
			}
		} finally {
			if (out != null)
				out.close();
		}
	}

}
