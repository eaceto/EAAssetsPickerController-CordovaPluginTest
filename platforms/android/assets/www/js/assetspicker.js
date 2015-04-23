/*global cordova,window,console*/
/**
 * An assets Picker plugin for Cordova
 * 
 * Developed by Ezequiel Aceto
 * Based on
 */

var EAAssetPicker = function() {

};

/*
 *	success - success callback
 *	fail - error callback
 *	options
 *		.MaxNumberOfAssetsToPick - max images to be selected, defaults to 15. If this is set to 1,
 *		                      upon selection of a single image, the plugin will return it.
 *		.MaxVideoLengthInSeconds - max length for a each of the selected videos
 */
EAAssetPicker.prototype.getPictures = function(success, fail, options) {
    if (!options) {
        options = {};
    }
    
    var params = {
    MaxNumberOfAssetsToPick: options.MaxNumberOfAssetsToPick ? options.MaxNumberOfAssetsToPick : 1,
    MaxVideoLengthInSeconds: options.MaxVideoLengthInSeconds ? options.MaxVideoLengthInSeconds : 240
    };
    
	return cordova.exec(success, fail, "EAAssetPicker", "pickAssets", [params]);
};

window.imagePicker = new EAAssetPicker();
