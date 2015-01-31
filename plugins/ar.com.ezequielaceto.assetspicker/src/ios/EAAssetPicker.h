//
//  EAAssetPicker.h
//  EAAssetsPickerPlugin
//
//  Created by Ezequiel Aceto on 01/30/2015
//
//

#import <Cordova/CDVPlugin.h>
#import "EAAssetsViewController.h"
#import "EAAssetPickerHelper.h"

@interface EAAssetPicker : CDVPlugin <UINavigationControllerDelegate>

@property (copy)   NSString* callbackId;

- (void) pickAssets:(CDVInvokedUrlCommand *)command;



@end
