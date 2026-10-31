package com.autodroid;

import android.content.Context;

/** Resolves pure backup validation errors at the Android presentation boundary. */
final class BackupMessages {
    private BackupMessages() {}

    static String describe(Context context, Exception exception) {
        if (!(exception instanceof MacroBackup.ValidationException)) {
            if (exception instanceof org.json.JSONException)
                return context.getString(R.string.backup_error_invalid_json);
            return exception.getMessage();
        }
        MacroBackup.ValidationException failure = (MacroBackup.ValidationException) exception;
        int id;
        switch (failure.error) {
            case TRAILING_CONTENT:
                id = R.string.backup_error_trailing_content;
                break;
            case INVALID_FILE:
                id = R.string.backup_error_invalid_file;
                break;
            case UNSUPPORTED_VERSION:
                id = R.string.backup_error_version;
                break;
            case TOO_MANY_RULES:
                id = R.string.backup_error_rule_limit;
                break;
            case MISSING_NAME:
                id = R.string.backup_error_missing_name;
                break;
            case INVALID_NUMBERS:
                id = R.string.backup_error_numbers_type;
                break;
            case TOO_MANY_NUMBERS:
                id = R.string.backup_error_number_limit;
                break;
            case INVALID_NUMBER:
                id = R.string.backup_error_number_type;
                break;
            case MISSING_NUMBERS:
                id = R.string.backup_error_missing_numbers;
                break;
            case INVALID_SOUND:
                id = R.string.backup_error_sound;
                break;
            case INVALID_ENABLED:
                id = R.string.backup_error_enabled;
                break;
            case MISSING_FIELD:
                id = R.string.backup_error_missing_field;
                break;
            case INVALID_FIELD:
                id = R.string.backup_error_invalid_field;
                break;
            case NOT_NUMBER:
                id = R.string.backup_error_numeric_field;
                break;
            case OUT_OF_RANGE:
                id = R.string.backup_error_field_range;
                break;
            default:
                id = R.string.backup_error_invalid_json;
        }
        return context.getString(id, failure.arguments);
    }
}
