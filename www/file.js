cordova.define("cordova-plugin-file.File", function (require, exports, module) {
    module.exports = {
        createDirectory: function (path, cb) {
            cordova.exec(
                success => {
                    cb(success);
                },
                cb(false),
                'File',
                'createDirectory',
                [path]
            );
        },
        deleteDirectory: function (path, cb) {
            cordova.exec(
                success => {
                    cb(success);
                },
                cb(false),
                'File',
                'deleteDirectory',
                [path]
            );
        },
        readString: function (path, cb) {
            cordova.exec(
                txt => {
                    cb(txt);
                },
                cb(null),
                'File',
                'readString',
                [path]
            );
        },
        readBuffer: function (path, cb) {
            cordova.exec(
                res => {
                    cb(res);
                },
                cb(null),
                'File',
                'readBuffer',
                [path]
            );
        },
        write: function (args, cb) {
            cordova.exec(
                success => {
                    cb(success);
                },
                cb(false),
                'File',
                'write',
                [args]
            );
        }
    };
});
