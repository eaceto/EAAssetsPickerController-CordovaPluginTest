//
//  EAAssetPickerHelper.h
//  EAAssetsPickerController
//
//  Created by Kimi on 30/01/2015.
//  Copyright (c) 2015 Ezequiel Aceto. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import "CTAssetsPickerController.h"

typedef  void(^HelperPickAssetCompletionBlock)(NSArray* assets);
typedef  void(^HelperPickPhotoCompletionBlock)(NSString* photoPath);

@interface EAAssetPickerHelper : NSObject <CTAssetsPickerControllerDelegate, UIActionSheetDelegate, UIAlertViewDelegate> {
    
}

@property (nonatomic) int maxNumberOfAssetsToPick;
@property (nonatomic) int maxVideoLengthInSeconds;


-(void)onPickImageAction:(UIViewController*)vc withSelectedAssets:(NSMutableArray*)selectedAssets onAssetPicked:(HelperPickAssetCompletionBlock) onAssetPicked onPhotoPicked:(HelperPickPhotoCompletionBlock) onPhotoPicked;


+(void)recordVideo:(UIViewController*)vc withDelegate:(id<UIImagePickerControllerDelegate>) delegate;
+(void)takePhoto:(UIViewController*)vc withDelegate:(id<UIImagePickerControllerDelegate>) delegate;
+(BOOL)base:(UIViewController*)vc picker:(UIImagePickerController*)picker didFinishPickingMediaWithInfo:(NSDictionary*)info;


@end
