package de.dennisguse.opentracks.util;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.io.file.TrackFileFormat;
import de.dennisguse.opentracks.io.file.TrackFilenameGenerator;
import de.dennisguse.opentracks.io.file.exporter.ExportService;
import de.dennisguse.opentracks.io.file.exporter.ExportServiceResultReceiver;
import de.dennisguse.opentracks.io.file.exporter.ExportTask;
import de.dennisguse.opentracks.io.file.exporter.TrackExporter;
import de.dennisguse.opentracks.settings.PreferencesUtils;
import de.dennisguse.opentracks.settings.SettingsActivity;

public class ExportUtils {

    private static final String TAG = ExportUtils.class.getSimpleName();

    public static void postWorkoutExport(Context context, Track.Id trackId) {
        if (PreferencesUtils.shouldInstantExportAfterWorkout()) {
            TrackFileFormat trackFileFormat = PreferencesUtils.getExportTrackFileFormat();
            DocumentFile directory = IntentUtils.toDocumentFile(context, PreferencesUtils.getDefaultExportDirectoryUri());

            if (directory == null || !directory.canWrite()) {
                Toast.makeText(context, R.string.export_cannot_write_to_dir, Toast.LENGTH_LONG).show();
                return;
            }

            ExportServiceResultReceiver resultReceiver = new ExportServiceResultReceiver(new Handler(), new ExportServiceResultReceiver.Receiver() {
                @Override
                public void onExportError(ExportTask unused) {
                    Intent intent = new Intent(context, SettingsActivity.class);
                    intent.putExtra(SettingsActivity.EXTRAS_CHECK_EXPORT_DIRECTORY, true);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            });

            ExportService.enqueue(context, resultReceiver, new ExportTask(null, trackFileFormat, List.of(trackId)), directory.getUri());
        }
    }

    public static boolean exportTrack(Context context, DocumentFile directory, ExportTask exportTask) {
        ContentProviderUtils contentProviderUtils = new ContentProviderUtils(context);
        List<Track> tracks = exportTask.getTrackIds().stream().map(contentProviderUtils::getTrack).collect(Collectors.toList());
        Uri exportDocumentFileUri;
        if (tracks.size() == 1) {
            exportDocumentFileUri = getExportDocumentFileUri(context, tracks.get(0), exportTask.getTrackFileFormat(), directory);
        } else {
            String filename = TrackFilenameGenerator.format(exportTask.getFilename(), exportTask.getTrackFileFormat());
            exportDocumentFileUri = getExportDocumentFileUri(context, filename, exportTask.getTrackFileFormat(), directory);
        }

        if (exportDocumentFileUri == null) {
            Log.e(TAG, "Couldn't create document file for export");
            return false;
        }

        TrackExporter trackExporter = exportTask.getTrackFileFormat().createTrackExporter(context, contentProviderUtils);
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(exportDocumentFileUri)) {
            if (trackExporter.writeTrack(tracks, outputStream)) {
                return true;
            } else {
                if (!DocumentFile.fromSingleUri(context, exportDocumentFileUri).delete()) {
                    Log.e(TAG, "Unable to delete exportDocumentFile");
                }
                Log.e(TAG, "Unable to export track");
                return false;
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Unable to open exportDocumentFile " + exportDocumentFileUri, e);
            return false;
        } catch (IOException e) {
            Log.e(TAG, "Unable to close exportDocumentFile output stream", e);
            return false;
        }
    }

    public static List<String> getAllFiles(Context context, Uri directoryUri) {
        List<String> fileNames = new ArrayList<>();
        final ContentResolver resolver = context.getContentResolver();
        final Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, DocumentsContract.getDocumentId(directoryUri));

        try (Cursor c = resolver.query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            while (c.moveToNext()) {
                fileNames.add(c.getString(0));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed query: " + e);
        }

        return fileNames;
    }

    private static Uri getExportDocumentFileUri(Context context, Track track, TrackFileFormat trackFileFormat, DocumentFile directory) {
        String exportFileName = PreferencesUtils.getTrackFileformatGenerator().format(track, trackFileFormat);
        return getExportDocumentFileUri(context, exportFileName, trackFileFormat, directory);
    }

    private static Uri getExportDocumentFileUri(Context context, String exportFileName, TrackFileFormat trackFileFormat, DocumentFile directory) {
        Uri exportDocumentFileUri = findFile(context, directory.getUri(), exportFileName);
        if (exportDocumentFileUri == null) {
            final DocumentFile file = directory.createFile(trackFileFormat.getMimeType(), exportFileName);
            if (file != null) {
                exportDocumentFileUri = file.getUri();
            }
        }
        return exportDocumentFileUri;
    }

    private static Uri findFile(Context context, Uri directoryUri, String exportFileName) {
        final ContentResolver resolver = context.getContentResolver();
        final Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, DocumentsContract.getDocumentId(directoryUri));

        try (Cursor c = resolver.query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {

            while (c.moveToNext()) {
                final String documentId = c.getString(0);
                final String documentName = c.getString(1);
                if (documentName.equals(exportFileName)) {
                    return DocumentsContract.buildDocumentUriUsingTree(directoryUri, documentId);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Find file error: failed query: " + e);
        }

        return null;
    }
}
