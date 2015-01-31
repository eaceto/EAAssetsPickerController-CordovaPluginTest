//
//  EAAssetPicker.m
//  EAAssetsPickerPlugin
//
//  Created by Ezequiel Aceto on 01/30/2015
//
//

#import "EAAssetPicker.h"
#import "EAAssetPickerHelper.h"
#import "ALAsset+assetType.h"

@interface EAAssetPicker()

@property (nonatomic, strong) EAAssetPickerHelper* helper;

@end

@implementation EAAssetPicker

@synthesize callbackId;



- (void) pickAssets:(CDVInvokedUrlCommand *)command {
    NSDictionary *options = [command.arguments objectAtIndex: 0];
    
    NSInteger maximumImagesCount = 1;
    if (options && [options objectForKey:@"MaxNumberOfAssetsToPick"]) {
        maximumImagesCount = [[options objectForKey:@"MaxNumberOfAssetsToPick"] integerValue];
    }
    NSInteger maximumVideoLength = 240;
    if (options && [options objectForKey:@"MaxVideoLengthInSeconds"]) {
        maximumVideoLength = [[options objectForKey:@"MaxVideoLengthInSeconds"] integerValue];
    }
    
    self.callbackId = command.callbackId;
    
    if (self.helper == nil) {
        self.helper = [[EAAssetPickerHelper alloc] init];
    }
    
    [self.helper setMaxVideoLengthInSeconds:maximumVideoLength];
    [self.helper setMaxNumberOfAssetsToPick:maximumImagesCount];
    
    [self.helper onPickImageAction:self.viewController
                withSelectedAssets:nil
                     onAssetPicked:^(NSArray* assets) {
                         NSLog(@"%@",assets);
                         
                         CDVPluginResult* result = nil;
                         
                         if (assets && [assets count] > 0) {
                             NSMutableArray *resultStrings = [[NSMutableArray alloc] init];
                             int i = 0;
                             for (ALAsset* asset in assets) {
                                 NSNumber *timestamp = [NSNumber numberWithDouble:[NSDate timeIntervalSinceReferenceDate]];
                                 if ([asset isPhoto]) {
                                     
                                     UIImage *image = [UIImage imageWithCGImage:[[asset defaultRepresentation] fullResolutionImage]];
                                     
                                     if (image != nil) {
                                         NSString  *pngPath = [NSHomeDirectory() stringByAppendingPathComponent:[NSString stringWithFormat:@"Documents/pictureTakenFromCamera%li-%i.png",(long)[timestamp integerValue],(int)i]];
                                         
                                         // Write image to PNG
                                         NSData* jpegData = UIImageJPEGRepresentation(image, 0.9);
                                         
                                         BOOL written = [jpegData writeToFile:pngPath atomically:YES];
                                         
                                         if (written == YES && pngPath != nil) {
                                             [resultStrings addObject:pngPath];
                                             i++;
                                         }
                                     }
                                 }
                             }
                             
                             
                             result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:resultStrings];
                         }
                         else {
                             NSArray* emptyArray = [NSArray array];
                             result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:emptyArray];
                         }
                         
                         [self.viewController dismissViewControllerAnimated:YES completion:nil];
                         [self.commandDelegate sendPluginResult:result callbackId:self.callbackId];
                     }
                     onPhotoPicked:^(NSString* photoPath) {
                         NSLog(@"%@",photoPath);
                         CDVPluginResult* result = nil;
                         
                         if (photoPath && [photoPath length] > 0) {
                             NSArray* photoArray = @[photoPath];
                             result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:photoArray];
                         }
                         else {
                             NSArray* emptyArray = [NSArray array];
                             result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:emptyArray];
                         }
                         
                         [self.viewController dismissViewControllerAnimated:YES completion:nil];
                         [self.commandDelegate sendPluginResult:result callbackId:self.callbackId];
                     }];
}

@end
