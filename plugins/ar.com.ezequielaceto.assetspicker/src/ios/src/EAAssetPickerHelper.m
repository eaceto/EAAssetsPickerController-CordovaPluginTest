//
//  EAAssetPickerHelper.m
//  EAAssetsPickerController
//
//  Created by Kimi on 30/01/2015.
//  Copyright (c) 2015 Ezequiel Aceto. All rights reserved.
//

#import "EAAssetPickerHelper.h"
#import <MobileCoreServices/MobileCoreServices.h>
#import "ALAsset+assetType.h"
#import "ALAsset+isEqual.h"

@interface EAAssetPickerHelper()
@property (readwrite, copy) HelperPickAssetCompletionBlock onPickAssetCompletionBlock;
@property (readwrite, copy) HelperPickPhotoCompletionBlock onPickPhotoCompletionBlock;
@end

@implementation EAAssetPickerHelper
@synthesize maxNumberOfAssetsToPick;
@synthesize maxVideoLengthInSeconds;

#pragma mark Image and Video
+(void)takePhoto:(UIViewController*)vc withDelegate:(id<UIImagePickerControllerDelegate, UINavigationControllerDelegate>) delegate {
    
    BOOL hasCamera = [UIImagePickerController isSourceTypeAvailable:UIImagePickerControllerSourceTypeCamera];
    if (hasCamera)
    {
        UIImagePickerController* imagePicker = [[UIImagePickerController alloc] init];
        imagePicker.mediaTypes = [NSArray arrayWithObjects:(NSString *)kUTTypeImage,nil];
        imagePicker.delegate = delegate;
        imagePicker.sourceType = UIImagePickerControllerSourceTypeCamera;
        imagePicker.allowsEditing = NO;
        
        [vc presentViewController:imagePicker animated:YES completion:^(){
            
        }];
    }
}

+(void)recordVideo:(UIViewController*)vc withDelegate:(id<UIImagePickerControllerDelegate,UINavigationControllerDelegate>) delegate {
    
    BOOL hasCamera = [UIImagePickerController isSourceTypeAvailable:UIImagePickerControllerSourceTypeCamera];
    if (hasCamera)
    {
        UIImagePickerController* imagePicker = [[UIImagePickerController alloc] init];
        imagePicker.mediaTypes = [NSArray arrayWithObjects:(NSString*)kUTTypeVideo,(NSString*)kUTTypeMovie,nil];
        imagePicker.delegate = delegate;
        imagePicker.sourceType = UIImagePickerControllerSourceTypeCamera;
        imagePicker.allowsEditing = YES;
        
        
        [vc presentViewController:imagePicker animated:YES completion:^(){

        }];
    }
}

+ (BOOL)base:(UIViewController*)baseVC picker:(UIImagePickerController *)picker didFinishPickingMediaWithInfo:(NSDictionary *)info {
    NSString* mediaType = [info objectForKey:UIImagePickerControllerMediaType];
    
    BOOL dismissPicker = YES;
    
    if (mediaType != nil && [mediaType compare:(NSString*)kUTTypeImage] == NSOrderedSame) {
        // grabar imagen
        UIImage* image = [info objectForKey:UIImagePickerControllerOriginalImage];
        
        if (image != nil) {
            NSNumber *timestamp = [NSNumber numberWithDouble:[NSDate timeIntervalSinceReferenceDate]];
            NSString  *pngPath = [NSHomeDirectory() stringByAppendingPathComponent:[NSString stringWithFormat:@"Documents/pictureTakenFromCamera%li.png",(long)[timestamp integerValue]]];
            
            // Write image to PNG
            NSData* jpegData = UIImageJPEGRepresentation(image, 0.9);
            
            BOOL written = [jpegData writeToFile:pngPath atomically:YES];
            
            if (written == YES && pngPath != nil) {
                NSLog(@"TODO IMPLEMENT");
            }
        }
    }
    else if (mediaType != nil && (([mediaType compare:(NSString*)kUTTypeVideo] == NSOrderedSame) || ([mediaType compare:(NSString*)kUTTypeMovie] == NSOrderedSame))){

        NSURL *imagePickerURL = [info objectForKey: UIImagePickerControllerMediaURL];
        NSString* moviePath = [imagePickerURL path];
        
        if (picker.sourceType == UIImagePickerControllerSourceTypeCamera) {
            if (UIVideoAtPathIsCompatibleWithSavedPhotosAlbum (moviePath)) {
                UISaveVideoAtPathToSavedPhotosAlbum(moviePath, self, @selector(video:didFinishSavingWithError:contextInfo:), nil);
            }
        }
        else {
            [EAAssetPickerHelper video:moviePath didFinishSavingWithError:nil contextInfo:nil];
        }
    }
    
    return dismissPicker;
}

+ (void)video:(NSString *) videoPath didFinishSavingWithError: (NSError *) error contextInfo: (void *) contextInfo {
    
    if ((videoPath == nil || (videoPath != nil && [@"" compare:videoPath] == NSOrderedSame)) && error == nil) {
        error = [NSError errorWithDomain:@"CameraPlugin" code:-1 userInfo:nil];
    }
    
    if(error) {
        //[data setObject:@"Sorry, your media could not be saved." forKey:@"error"];
        //TODO ALERT
        UIAlertView* errorOnVideo = [[UIAlertView alloc] initWithTitle:@"Image Picker" message:@"Sorry, there was an error while processing your request." delegate:nil cancelButtonTitle:@"Cancel" otherButtonTitles: nil];
        [errorOnVideo show];
    }
    else {
        NSLog(@"TODO IMPLEMENT");
    }
}

-(void)onPickImageAction:(UIViewController*)vc withSelectedAssets:(NSMutableArray*)selectedAssets onAssetPicked:(HelperPickAssetCompletionBlock) onAssetPicked onPhotoPicked:(HelperPickPhotoCompletionBlock) onPhotoPicked {
    // Dummy
    if (selectedAssets == nil) selectedAssets = [[NSMutableArray alloc] init];
    
    CTAssetsPickerController *picker = [[CTAssetsPickerController alloc] init];
    picker.assetsFilter         = [ALAssetsFilter allAssets];
    picker.showsCancelButton    = YES;
    picker.delegate             = self;
    picker.selectedAssets       = selectedAssets;
    self.onPickAssetCompletionBlock = onAssetPicked;
    self.onPickPhotoCompletionBlock = onPhotoPicked;
    
    [vc presentViewController:picker animated:YES completion:^(){

    }];
}


- (BOOL)assetsPickerController:(CTAssetsPickerController *)picker isDefaultAssetsGroup:(ALAssetsGroup *)group
{
    return ([[group valueForProperty:ALAssetsGroupPropertyType] integerValue] == ALAssetsGroupSavedPhotos);
}

- (void)assetsPickerController:(CTAssetsPickerController *)picker didFinishPickingAssets:(NSArray *)assets
{
    if (self.onPickAssetCompletionBlock) {
        self.onPickAssetCompletionBlock(assets);
    }
    [picker dismissViewControllerAnimated:YES completion:^(){}];
}

- (BOOL)assetsPickerController:(CTAssetsPickerController *)picker shouldEnableAsset:(ALAsset *)asset
{
    // Enable video clips if they are at max MainViewHelperMAX_VIDEO_LENGTH seconds
    if ([[asset valueForProperty:ALAssetPropertyType] isEqual:ALAssetTypeVideo])
    {
        NSTimeInterval duration = [[asset valueForProperty:ALAssetPropertyDuration] doubleValue];
        return lround(duration) <= maxVideoLengthInSeconds;
    }
    else
    {
        return YES;
    }
}

- (BOOL)assetsPickerController:(CTAssetsPickerController *)picker shouldSelectAsset:(ALAsset *)asset
{
    // TODO Translate
    
    if (picker.selectedAssets.count >= maxNumberOfAssetsToPick)
    {
        UIAlertView *alertView =
        [[UIAlertView alloc] initWithTitle:@"Attention"
                                   message:[NSString stringWithFormat:@"Please select not more than %i media",maxNumberOfAssetsToPick]
                                  delegate:nil
                         cancelButtonTitle:nil
                         otherButtonTitles:@"Done", nil];
        
        [alertView show];
    }
    
    if (asset == nil) return picker.selectedAssets.count < maxNumberOfAssetsToPick;
    
    if (!asset.defaultRepresentation)
    {
        UIAlertView *alertView =
        [[UIAlertView alloc] initWithTitle:@"Attention"
                                   message:@"Your media has not yet been downloaded to your device"
                                  delegate:nil
                         cancelButtonTitle:nil
                         otherButtonTitles:@"Done", nil];
        
        [alertView show];
    }
    
    
    return (picker.selectedAssets.count < maxNumberOfAssetsToPick && asset.defaultRepresentation != nil);
}
#pragma mark -

@end
