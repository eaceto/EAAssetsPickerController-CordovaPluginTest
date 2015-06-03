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
    NSDictionary *options = nil;
    if (command && command.arguments && [command.arguments count] > 0) {
        options = [command.arguments objectAtIndex: 0];
    }
    
    NSInteger maximumImagesCount = 1;
    if (options && [options objectForKey:@"MaxNumberOfAssetsToPick"]) {
        maximumImagesCount = [[options objectForKey:@"MaxNumberOfAssetsToPick"] integerValue];
    }
    NSInteger maximumVideoLength = 240;
    if (options && [options objectForKey:@"MaxVideoLengthInSeconds"]) {
        maximumVideoLength = [[options objectForKey:@"MaxVideoLengthInSeconds"] integerValue];
    }
    
    NSString* contentType = @"all";
    if (options && [options objectForKey:@"ContentType"]) {
        contentType = [options objectForKey:@"ContentType"];
    }
    
    self.callbackId = command.callbackId;
    
    if (self.helper == nil) {
        self.helper = [[EAAssetPickerHelper alloc] init];
    }
    
    [self.helper setMaxVideoLengthInSeconds:(int)maximumVideoLength];
    [self.helper setMaxNumberOfAssetsToPick:(int)maximumImagesCount];
    [self.helper setContentType:contentType];
    
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
                                 else if ([asset isVideo]) {
                                     ALAssetRepresentation *rep = [asset defaultRepresentation];
                                     
                                     if (rep) {
                                         Byte *buffer = (Byte*)malloc(rep.size);
                                         NSUInteger buffered = [rep getBytes:buffer fromOffset:0.0 length:rep.size error:nil];
                                         NSData *data = [NSData dataWithBytesNoCopy:buffer length:buffered freeWhenDone:YES];
                                         
                                         NSString  *videoPath = [NSHomeDirectory() stringByAppendingPathComponent:[NSString stringWithFormat:@"Documents/videoTakenFromCamera%li-%i.mov",(long)[timestamp integerValue],(int)i]];
                                         
                                         
                                         BOOL written = [data writeToFile:videoPath atomically:YES];
                                         
                                         if (written == YES && videoPath != nil) {
                                             [resultStrings addObject:videoPath];
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
