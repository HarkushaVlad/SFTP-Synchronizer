# SFTP-Synchronizer

SFTP-Synchronizer is an Android application designed to synchronize folder states and files between
your mobile device and an SFTP server. The app offers a set of features to ensure that your
directories and files remain up-to-date across both platforms.

## Features

- **File Synchronization**: Detects new files and replaces older versions on either the phone or the
  SFTP server.
- **File Addition**: Automatically adds new files from one device to the other.
- **Directory Mirroring**: Ensures complete directory mirroring by deleting files that are no longer
  present in the source.

## Setup

To successfully synchronize folders between your phone and an SFTP server, you'll need to configure
the following:

1. **SFTP Connection Details**: Enter your SFTP credentials in the app settings.
2. **Directory Paths**: Specify the path to the directory on both the phone and the SFTP server.
   Ensure that directory names are identical.
3. **File Names**: For file synchronization, ensure that the filenames match between the phone and
   the server.

### Additional Setup Details

- **Phone Directory Selection**: Use the app’s graphical file picker to choose the directory on your
  phone.
- **SFTP Path Configuration**: Utilize the app’s auto-paste feature to automatically fill the SFTP
  path if a base directory is specified in the settings.

## Synchronization Process

During the synchronization, the app will:

- **Replace Older Files**: If a file is outdated, the app will prompt you for confirmation before
  replacing it, unless auto-confirmation is enabled.
- **Delete Files**: File deletions are confirmed via a prompt, unless auto-confirmation is enabled.
- **Add New Files**: New files are copied without additional confirmation since there’s no risk of
  data loss.

> **Note**: File deletions can only be detected if the directory structure has been scanned and
> logged beforehand. If the structure was not logged, deleted files won’t be detected, and they
> might
> be duplicated if missing on the opposite platform.

## Two Files Synchronization Mode

This mode is designed for synchronizing exactly two files instead of directories. It supports only
file replacement and copying; file deletion is not available in this mode.

## Additional Features

- **Auto-Confirm**: Enable the auto-confirm feature to automatically accept all changes without user
  prompts.
- **Logs**: View synchronization logs and file operation records for troubleshooting. Logs can be
  accessed via the 'Settings' menu.
- **File Deletion Tracking**: Optionally disable file deletion tracking to prevent the app from
  detecting and processing file deletions. This can be useful if you want to avoid unnecessary
  operations or if you handle file deletions manually.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
