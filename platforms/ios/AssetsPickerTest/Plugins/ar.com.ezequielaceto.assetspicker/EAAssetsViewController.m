/*
 CTAssetsViewController.m
 
 The MIT License (MIT)
 
 Copyright (c) 2013 Clement CN Tsang
 
 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:
 
 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.
 
 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 
 */
#import <MobileCoreServices/MobileCoreServices.h>
#import "CTAssetsPickerCommon.h"
#import "CTAssetsPickerController.h"
#import "EAAssetsViewController.h"
#import "CTAssetsViewCell.h"
#import "CTAssetsSupplementaryView.h"
#import "CTAssetsPageViewController.h"
#import "CTAssetsViewControllerTransition.h"
#import "EAAssetPickerHelper.h"
#import "ALAssetsLibrary+CustomPhotoAlbum.h"

NSString * const CTAssetsViewControllerRecordVideoTag = @"kRecordVideoTag";
NSString * const CTAssetsViewControllerTakePictureTag = @"kTakePictureTag";



NSString * const CTAssetsViewCellIdentifier = @"CTAssetsViewCellIdentifier";
NSString * const CTAssetsSupplementaryViewIdentifier = @"CTAssetsSupplementaryViewIdentifier";

int const kCustomButtonsCount = 2;

BOOL const allowsMultipleSelection = YES;

@interface CTAssetsPickerController ()

- (void)finishPickingAssets:(id)sender;

- (NSString *)toolbarTitle;
- (UIView *)noAssetsView;

@end



@interface EAAssetsViewController ()

@property (nonatomic, weak) CTAssetsPickerController *picker;
@property (nonatomic, strong) NSMutableArray *assets;

@end





@implementation EAAssetsViewController
@synthesize contentType;

- (id)init
{
    UICollectionViewFlowLayout *layout = [self collectionViewFlowLayoutOfOrientation:self.interfaceOrientation];
    
    if (self = [super initWithCollectionViewLayout:layout])
    {
        self.collectionView.allowsMultipleSelection = allowsMultipleSelection;
        
        [self.collectionView registerClass:CTAssetsViewCell.class
                forCellWithReuseIdentifier:CTAssetsViewCellIdentifier];
        
        [self.collectionView registerClass:CTAssetsSupplementaryView.class
                forSupplementaryViewOfKind:UICollectionElementKindSectionFooter
                       withReuseIdentifier:CTAssetsSupplementaryViewIdentifier];
        
        self.preferredContentSize = CTAssetPickerPopoverContentSize;
    }
    
    [self addNotificationObserver];
    [self addGestureRecognizer];
    
    return self;
}

- (void)viewDidLoad
{
    [super viewDidLoad];
    [self setupViews];
}

- (void)viewWillAppear:(BOOL)animated
{
    [super viewWillAppear:animated];
    [self setupButtons];
    [self setupToolbar];
    [self setupAssets];
}

- (void)dealloc
{
    [self removeNotificationObserver];
}


#pragma mark - Accessors

- (CTAssetsPickerController *)picker
{
    return (CTAssetsPickerController *)self.navigationController.parentViewController;
}


#pragma mark - Rotation

- (void)willAnimateRotationToInterfaceOrientation:(UIInterfaceOrientation)toInterfaceOrientation duration:(NSTimeInterval)duration
{
    UICollectionViewFlowLayout *layout = [self collectionViewFlowLayoutOfOrientation:toInterfaceOrientation];
    [self.collectionView setCollectionViewLayout:layout animated:YES];
}


#pragma mark - Setup

- (void)setupViews
{
    self.collectionView.backgroundColor = [UIColor whiteColor];
}

- (void)setupButtons
{
    self.navigationItem.rightBarButtonItem =
    [[UIBarButtonItem alloc] initWithTitle:NSLocalizedStringFromTable(@"Done", @"CTAssetsPickerController", nil)
                                     style:UIBarButtonItemStyleDone
                                    target:self.picker
                                    action:@selector(finishPickingAssets:)];
    
    if (self.picker.alwaysEnableDoneButton)
        self.navigationItem.rightBarButtonItem.enabled = YES;
    else
        self.navigationItem.rightBarButtonItem.enabled = (self.picker.selectedAssets.count > 0);
}

- (void)setupToolbar
{
    self.toolbarItems = self.picker.toolbarItems;
}

- (void)setupAssets
{
    self.title = [self.assetsGroup valueForProperty:ALAssetsGroupPropertyName];
    
    if (!self.assets)
        self.assets = [[NSMutableArray alloc] init];
    else
        [self.assets removeAllObjects]; // return;
    
    // Add take picture and record video
    {
    //    [self.assets addObject:CTAssetsViewControllerTakePictureTag];
    //    [self.assets addObject:CTAssetsViewControllerRecordVideoTag];
    }
    
    ALAssetsGroupEnumerationResultsBlock resultsBlock = ^(ALAsset *asset, NSUInteger index, BOOL *stop)
    {
        if (asset)
        {
            BOOL shouldShowAsset;
            
            if ([self.picker.delegate respondsToSelector:@selector(assetsPickerController:shouldShowAsset:)])
                shouldShowAsset = [self.picker.delegate assetsPickerController:self.picker shouldShowAsset:asset];
            else
                shouldShowAsset = YES;
            
            if (shouldShowAsset)
                [self.assets addObject:asset];
        }
        else
        {
            NSArray* a = [self.assets sortedArrayUsingComparator:^NSComparisonResult(id obj1, id obj2){
                if (obj1 && obj2) {
                    ALAsset* a1 = (ALAsset*)obj1;
                    ALAsset* a2 = (ALAsset*)obj2;
                    
                    NSDate* d1 = [a1 valueForProperty:ALAssetPropertyDate];
                    NSDate* d2 = [a2 valueForProperty:ALAssetPropertyDate];
                    
                    if (d1 && d2 == nil) return NSOrderedDescending;
                    if (d2 && d1 == nil) return NSOrderedAscending;
                    if ([d1 compare:d2] == NSOrderedAscending) return NSOrderedDescending;
                    if ([d1 compare:d2] == NSOrderedDescending) return NSOrderedAscending;
                }
                return NSOrderedSame;
            }];
            
            [self.assets removeAllObjects];
            
            if ([@"all" compare:contentType] == NSOrderedSame || [@"photos" compare:contentType] == NSOrderedSame) {
                [self.assets addObject:CTAssetsViewControllerTakePictureTag];
            }
            if ([@"all" compare:contentType] == NSOrderedSame || [@"videos" compare:contentType] == NSOrderedSame) {
                [self.assets addObject:CTAssetsViewControllerRecordVideoTag];
            }
            
            [self.assets addObjectsFromArray:a];
            
            [self reloadData];
        }
    };
    
    [self.assetsGroup enumerateAssetsUsingBlock:resultsBlock];
}


#pragma mark - Collection View Layout

- (UICollectionViewFlowLayout *)collectionViewFlowLayoutOfOrientation:(UIInterfaceOrientation)orientation
{
    UICollectionViewFlowLayout *layout = [[UICollectionViewFlowLayout alloc] init];
    
    layout.itemSize             = CTAssetThumbnailSize;
    layout.footerReferenceSize  = CGSizeMake(0, 47.0);
    
    if (UIInterfaceOrientationIsLandscape(orientation) && (UI_USER_INTERFACE_IDIOM() != UIUserInterfaceIdiomPad))
    {
        layout.sectionInset            = UIEdgeInsetsMake(9.0, 2.0, 0, 2.0);
        layout.minimumInteritemSpacing = (CTIPhone6Plus) ? 1.0 : ( (CTIPhone6) ? 2.0 : 3.0 );
        layout.minimumLineSpacing      = (CTIPhone6Plus) ? 1.0 : ( (CTIPhone6) ? 2.0 : 3.0 );
    }
    else
    {
        layout.sectionInset            = UIEdgeInsetsMake(9.0, 0, 0, 0);
        layout.minimumInteritemSpacing = (CTIPhone6Plus) ? 0.5 : ( (CTIPhone6) ? 1.0 : 2.0 );
        layout.minimumLineSpacing      = (CTIPhone6Plus) ? 0.5 : ( (CTIPhone6) ? 1.0 : 2.0 );
    }
    
    return layout;
}


#pragma mark - Notifications

- (void)addNotificationObserver
{
    NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
    
    [center addObserver:self
               selector:@selector(assetsLibraryChanged:)
                   name:ALAssetsLibraryChangedNotification
                 object:nil];
    
    [center addObserver:self
               selector:@selector(selectedAssetsChanged:)
                   name:CTAssetsPickerSelectedAssetsChangedNotification
                 object:nil];
}

- (void)removeNotificationObserver
{
    [[NSNotificationCenter defaultCenter] removeObserver:self name:ALAssetsLibraryChangedNotification object:nil];
    [[NSNotificationCenter defaultCenter] removeObserver:self name:CTAssetsPickerSelectedAssetsChangedNotification object:nil];
}


#pragma mark - Assets Library Changed

- (void)assetsLibraryChanged:(NSNotification *)notification
{
    // Reload all assets
    if (notification.userInfo == nil)
        [self performSelectorOnMainThread:@selector(reloadAssets) withObject:nil waitUntilDone:NO];
    
    // Reload effected assets groups
    if (notification.userInfo.count > 0)
        [self reloadAssetsGroupForUserInfo:notification.userInfo];
}


#pragma mark - Reload Assets Group

- (void)reloadAssetsGroupForUserInfo:(NSDictionary *)userInfo
{
    NSSet *URLs = [userInfo objectForKey:ALAssetLibraryUpdatedAssetGroupsKey];
    NSURL *URL  = [self.assetsGroup valueForProperty:ALAssetsGroupPropertyURL];
    
    NSPredicate *predicate = [NSPredicate predicateWithFormat:@"SELF == %@", URL];
    NSArray *matchedGroups = [URLs.allObjects filteredArrayUsingPredicate:predicate];
    
    // Reload assets if current assets group is updated
    if (matchedGroups.count > 0)
        [self performSelectorOnMainThread:@selector(reloadAssets) withObject:nil waitUntilDone:NO];
}



#pragma mark - Selected Assets Changed

- (void)selectedAssetsChanged:(NSNotification *)notification
{
    NSArray *selectedAssets = (NSArray *)notification.object;
    
    [[self.toolbarItems objectAtIndex:1] setTitle:[self.picker toolbarTitle]];
    
    [self.navigationController setToolbarHidden:(selectedAssets.count == 0) animated:YES];
}



#pragma mark - Gesture Recognizer

- (void)addGestureRecognizer
{
    UILongPressGestureRecognizer *longPress =
    [[UILongPressGestureRecognizer alloc] initWithTarget:self action:@selector(pushPageViewController:)];
    
    [self.collectionView addGestureRecognizer:longPress];
}


#pragma mark - Push Assets Page View Controller

- (void)pushPageViewController:(UILongPressGestureRecognizer *)longPress
{
    if (longPress.state == UIGestureRecognizerStateBegan)
    {
        CGPoint point           = [longPress locationInView:self.collectionView];
        NSIndexPath *indexPath  = [self.collectionView indexPathForItemAtPoint:point];
        
        CTAssetsPageViewController *vc = [[CTAssetsPageViewController alloc] initWithAssets:self.assets];
        vc.pageIndex = indexPath.item;
        
        [self.navigationController pushViewController:vc animated:YES];
    }
}



#pragma mark - Reload Assets

- (void)reloadAssets
{
    self.assets = nil;
    [self setupAssets];
}



#pragma mark - Reload Data

- (void)reloadData
{
    if (self.assets.count > 0)
    {
        [self.collectionView reloadData];
        
        /*
         if (self.collectionView.contentOffset.y <= 0)
         [self.collectionView setContentOffset:CGPointMake(0, self.collectionViewLayout.collectionViewContentSize.height)];
         */
    }
    else
    {
        [self showNoAssets];
    }
}


#pragma mark - No assets

- (void)showNoAssets
{
    self.collectionView.backgroundView = [self.picker noAssetsView];
    [self setAccessibilityFocus];
}

- (void)setAccessibilityFocus
{
    self.collectionView.isAccessibilityElement  = YES;
    self.collectionView.accessibilityLabel      = self.collectionView.backgroundView.accessibilityLabel;
    UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, self.collectionView);
}


#pragma mark - Collection View Data Source

- (NSInteger)numberOfSectionsInCollectionView:(UICollectionView *)collectionView
{
    return 1;
}

- (NSInteger)collectionView:(UICollectionView *)collectionView numberOfItemsInSection:(NSInteger)section
{
    return self.assets.count;
}

- (UICollectionViewCell *)collectionView:(UICollectionView *)collectionView cellForItemAtIndexPath:(NSIndexPath *)indexPath
{
    if ([@"all" compare:contentType] == NSOrderedSame) {
        if (indexPath.row == 0 || indexPath.row == 1) {
            CTAssetsViewCell *cell =
            [collectionView dequeueReusableCellWithReuseIdentifier:CTAssetsViewCellIdentifier
                                                      forIndexPath:indexPath];
            cell.enabled = YES;
            
            [cell bindCustomButton:(indexPath.row == 0 ? CTAssetsViewControllerTakePictureTag : CTAssetsViewControllerRecordVideoTag)];
            
            return cell;
        }
    }
    else if ([@"photos" compare:contentType] == NSOrderedSame && indexPath.row == 0) {
        CTAssetsViewCell *cell =
        [collectionView dequeueReusableCellWithReuseIdentifier:CTAssetsViewCellIdentifier
                                                  forIndexPath:indexPath];
        cell.enabled = YES;
        
        [cell bindCustomButton:CTAssetsViewControllerTakePictureTag];
        
        return cell;
    }
    else if ([@"videos" compare:contentType] == NSOrderedSame && indexPath.row == 0) {
        CTAssetsViewCell *cell =
        [collectionView dequeueReusableCellWithReuseIdentifier:CTAssetsViewCellIdentifier
                                                  forIndexPath:indexPath];
        cell.enabled = YES;
        
        [cell bindCustomButton:CTAssetsViewControllerRecordVideoTag];
        
        return cell;
    }
    

    {
        CTAssetsViewCell *cell =
        [collectionView dequeueReusableCellWithReuseIdentifier:CTAssetsViewCellIdentifier
                                                  forIndexPath:indexPath];
        
        ALAsset *asset = [self.assets objectAtIndex:indexPath.row];
        
        if ([self.picker.delegate respondsToSelector:@selector(assetsPickerController:shouldEnableAsset:)])
            cell.enabled = [self.picker.delegate assetsPickerController:self.picker shouldEnableAsset:asset];
        else
            cell.enabled = YES;
        
        // XXX
        // Setting `selected` property blocks further deselection.
        // Have to call selectItemAtIndexPath too. ( ref: http://stackoverflow.com/a/17812116/1648333 )
        if ([self.picker.selectedAssets containsObject:asset])
        {
            cell.selected = YES;
            [collectionView selectItemAtIndexPath:indexPath animated:NO scrollPosition:UICollectionViewScrollPositionNone];
        }
        
        [cell bind:asset];
        
        return cell;
    }
}

- (UICollectionReusableView *)collectionView:(UICollectionView *)collectionView viewForSupplementaryElementOfKind:(NSString *)kind atIndexPath:(NSIndexPath *)indexPath
{
    CTAssetsSupplementaryView *view =
    [collectionView dequeueReusableSupplementaryViewOfKind:UICollectionElementKindSectionFooter
                                       withReuseIdentifier:CTAssetsSupplementaryViewIdentifier
                                              forIndexPath:indexPath];
    
    [view bind:self.assets];
    
    if (self.assets.count == 0)
        view.hidden = YES;
    
    return view;
}

#pragma mark - Picker delegate
- (void)imagePickerController:(UIImagePickerController *)picker didFinishPickingMediaWithInfo:(NSDictionary *)info {
    NSLog(@"");
    
    BOOL shouldDismiss = YES;
    if (info != nil) {
        
        NSString* mediaType = [info objectForKey:UIImagePickerControllerMediaType];
        
        if (mediaType != nil && [mediaType compare:(NSString*)kUTTypeImage] == NSOrderedSame) {
            // grabar imagen
            UIImage* image = [info objectForKey:UIImagePickerControllerOriginalImage];
            
            if (image != nil) {
                /*
                 NSNumber *timestamp = [NSNumber numberWithDouble:[NSDate timeIntervalSinceReferenceDate]];
                 NSString  *pngPath = [NSHomeDirectory() stringByAppendingPathComponent:[NSString stringWithFormat:@"Documents/pictureTakenFromCamera%li.png",(long)[timestamp integerValue]]];
                 
                 // Write image to PNG
                 NSData* jpegData = UIImageJPEGRepresentation(image, 1.0);
                 
                 BOOL written = [jpegData writeToFile:pngPath atomically:YES];
                 
                 if (written == YES && pngPath != nil) {
                 }
                 */
                ALAssetsLibrary* lib = [[ALAssetsLibrary alloc] init];
                
                shouldDismiss = NO;
                
                __weak UIViewController* weakPicker = picker;

                [lib writeImageToSavedPhotosAlbum:image.CGImage orientation:(ALAssetOrientation)image.imageOrientation
                                   completionBlock:^(NSURL* assetURL, NSError* error) {
                                       
                                          //then get the image asseturl
                                          [lib assetForURL:assetURL
                                               resultBlock:^(ALAsset *asset) {
                                                   [self.assetsGroup addAsset:asset];
                                                   [weakPicker dismissViewControllerAnimated:YES completion:^(){}];
                                               } failureBlock:^(NSError *error) {
                                            [weakPicker dismissViewControllerAnimated:YES completion:^(){}];
                                               }];
                                      }];
                
            }
        }
        else if (mediaType != nil && (([mediaType compare:(NSString*)kUTTypeVideo] == NSOrderedSame) || ([mediaType compare:(NSString*)kUTTypeMovie] == NSOrderedSame))){
            // grabar el video
            //NSString *moviePath = [[info objectForKey: UIImagePickerControllerMediaURL] path];
            NSURL *imagePickerURL = [info objectForKey: UIImagePickerControllerMediaURL];
            NSString* moviePath = [imagePickerURL path];
            
            ALAssetsLibrary* lib = [[ALAssetsLibrary alloc] init];
            
            shouldDismiss = NO;
            
            __weak UIViewController* weakPicker = picker;

            [lib writeVideoAtPathToSavedPhotosAlbum:imagePickerURL completionBlock:^(NSURL* assetURL, NSError* error){
                //then get the image asseturl
                [lib assetForURL:assetURL
                     resultBlock:^(ALAsset *asset) {
                         [self.assetsGroup addAsset:asset];
                         [weakPicker dismissViewControllerAnimated:YES completion:^(){}];
                     } failureBlock:^(NSError *error) {
                         [weakPicker dismissViewControllerAnimated:YES completion:^(){}];
                     }];
            }];
            
            
            /*
             if (picker.sourceType == UIImagePickerControllerSourceTypeCamera) {
             if (UIVideoAtPathIsCompatibleWithSavedPhotosAlbum (moviePath)) {
             UISaveVideoAtPathToSavedPhotosAlbum(moviePath, self, @selector(video:didFinishSavingWithError:contextInfo:), nil);
             }
             }
             else {
             [MainViewHelper video:moviePath didFinishSavingWithError:nil contextInfo:nil];
             }*/
        }
    }
    
    if (shouldDismiss) {
        [picker dismissViewControllerAnimated:YES completion:^(){}];
    }
}


#pragma mark - Collection View Delegate

- (BOOL)collectionView:(UICollectionView *)collectionView shouldSelectItemAtIndexPath:(NSIndexPath *)indexPath
{
    if ([@"all" compare:contentType] == NSOrderedSame) {
        if (indexPath.row == 0) {
            BOOL canTakePhoto = YES;
            if ([self.picker.delegate respondsToSelector:@selector(assetsPickerController:shouldSelectAsset:)])
                canTakePhoto = [self.picker.delegate assetsPickerController:self.picker shouldSelectAsset:nil];
            
            if (canTakePhoto) {
                [EAAssetPickerHelper takePhoto:self withDelegate:self];
            }
            return NO;
        }
        if (indexPath.row == 1) {
            BOOL canRecordVideo = YES;
            if ([self.picker.delegate respondsToSelector:@selector(assetsPickerController:shouldSelectAsset:)])
                canRecordVideo = [self.picker.delegate assetsPickerController:self.picker shouldSelectAsset:nil];
            
            if (canRecordVideo) {
                [EAAssetPickerHelper recordVideo:self withDelegate:self];
            }
            return NO;
        }
    }
    else if ([@"photos" compare:contentType] == NSOrderedSame && indexPath.row == 0) {
        BOOL canTakePhoto = YES;
        if ([self.picker.delegate respondsToSelector:@selector(assetsPickerController:shouldSelectAsset:)])
            canTakePhoto = [self.picker.delegate assetsPickerController:self.picker shouldSelectAsset:nil];
        
        if (canTakePhoto) {
            [EAAssetPickerHelper takePhoto:self withDelegate:self];
        }
        return NO;
    }
    else if ([@"videos" compare:contentType] == NSOrderedSame && indexPath.row == 0) {
        BOOL canRecordVideo = YES;
        if ([self.picker.delegate respondsToSelector:@selector(assetsPickerController:shouldSelectAsset:)])
            canRecordVideo = [self.picker.delegate assetsPickerController:self.picker shouldSelectAsset:nil];
        
        if (canRecordVideo) {
            [EAAssetPickerHelper recordVideo:self withDelegate:self];
        }
        return NO;
    }
    
    ALAsset *asset = [self.assets objectAtIndex:indexPath.row];
    
    CTAssetsViewCell *cell = (CTAssetsViewCell *)[collectionView cellForItemAtIndexPath:indexPath];
    
    if (!cell.isEnabled)
        return NO;
    else if ([self.picker.delegate respondsToSelector:@selector(assetsPickerController:shouldSelectAsset:)])
        return [self.picker.delegate assetsPickerController:self.picker shouldSelectAsset:asset];
    else
        return YES;
}

- (void)collectionView:(UICollectionView *)collectionView didSelectItemAtIndexPath:(NSIndexPath *)indexPath
{
    if ([@"all" compare:contentType] == NSOrderedSame) {
        if (indexPath.row == 0 || indexPath.row == 1) {
            return;
        }
    }
    else if ([@"photos" compare:contentType] == NSOrderedSame && indexPath.row == 0) {
        return;
    }
    else if ([@"videos" compare:contentType] == NSOrderedSame && indexPath.row == 0) {
        return;
    }
    
    ALAsset *asset = [self.assets objectAtIndex:indexPath.row];
    
    [self.picker selectAsset:asset];
    
    if ([self.picker.delegate respondsToSelector:@selector(assetsPickerController:didSelectAsset:)])
        [self.picker.delegate assetsPickerController:self.picker didSelectAsset:asset];
}

- (BOOL)collectionView:(UICollectionView *)collectionView shouldDeselectItemAtIndexPath:(NSIndexPath *)indexPath
{
    
    if ([@"all" compare:contentType] == NSOrderedSame) {
        if (indexPath.row == 0 || indexPath.row == 1) {
            return YES;
        }
    }
    else if ([@"photos" compare:contentType] == NSOrderedSame && indexPath.row == 0) {
        return YES;
    }
    else if ([@"videos" compare:contentType] == NSOrderedSame && indexPath.row == 0) {
        return YES;
    }
    
    
    ALAsset *asset = [self.assets objectAtIndex:indexPath.row];
    
    if ([self.picker.delegate respondsToSelector:@selector(assetsPickerController:shouldDeselectAsset:)])
        return [self.picker.delegate assetsPickerController:self.picker shouldDeselectAsset:asset];
    else
        return YES;
}

- (void)collectionView:(UICollectionView *)collectionView didDeselectItemAtIndexPath:(NSIndexPath *)indexPath
{
    if ([@"all" compare:contentType] == NSOrderedSame) {
        if (indexPath.row == 0 || indexPath.row == 1) {
            return;
        }
    }
    else if ([@"photos" compare:contentType] == NSOrderedSame && indexPath.row == 0) {
        return;
    }
    else if ([@"videos" compare:contentType] == NSOrderedSame && indexPath.row == 0) {
        return;
    }
    
    ALAsset *asset = [self.assets objectAtIndex:indexPath.row];
    
    [self.picker deselectAsset:asset];
    
    if ([self.picker.delegate respondsToSelector:@selector(assetsPickerController:didDeselectAsset:)])
        [self.picker.delegate assetsPickerController:self.picker didDeselectAsset:asset];
}

- (BOOL)collectionView:(UICollectionView *)collectionView shouldHighlightItemAtIndexPath:(NSIndexPath *)indexPath
{
    if ([@"all" compare:contentType] == NSOrderedSame) {
        if (indexPath.row == 0 || indexPath.row == 1) {
            return YES;
        }
    }
    else if ([@"photos" compare:contentType] == NSOrderedSame && indexPath.row == 0) {
        return YES;
    }
    else if ([@"videos" compare:contentType] == NSOrderedSame && indexPath.row == 0) {
        return YES;
    }

    
    ALAsset *asset = [self.assets objectAtIndex:indexPath.row];
    
    if ([self.picker.delegate respondsToSelector:@selector(assetsPickerController:shouldHighlightAsset:)])
        return [self.picker.delegate assetsPickerController:self.picker shouldHighlightAsset:asset];
    else
        return YES;
}

- (void)collectionView:(UICollectionView *)collectionView didHighlightItemAtIndexPath:(NSIndexPath *)indexPath
{
    if ([@"all" compare:contentType] == NSOrderedSame) {
        if (indexPath.row == 0 || indexPath.row == 1) {
            return;
        }
    }
    else if ([@"photos" compare:contentType] == NSOrderedSame && indexPath.row == 0) {
        return;
    }
    else if ([@"videos" compare:contentType] == NSOrderedSame && indexPath.row == 0) {
        return;
    }
    
    ALAsset *asset = [self.assets objectAtIndex:indexPath.row];
    
    if ([self.picker.delegate respondsToSelector:@selector(assetsPickerController:didHighlightAsset:)])
        [self.picker.delegate assetsPickerController:self.picker didHighlightAsset:asset];
}

- (void)collectionView:(UICollectionView *)collectionView didUnhighlightItemAtIndexPath:(NSIndexPath *)indexPath
{
    if ([@"all" compare:contentType] == NSOrderedSame) {
        if (indexPath.row == 0 || indexPath.row == 1) {
            return;
        }
    }
    else if ([@"photos" compare:contentType] == NSOrderedSame && indexPath.row == 0) {
        return;
    }
    else if ([@"videos" compare:contentType] == NSOrderedSame && indexPath.row == 0) {
        return;
    }
    
    ALAsset *asset = [self.assets objectAtIndex:indexPath.row];
    
    if ([self.picker.delegate respondsToSelector:@selector(assetsPickerController:didUnhighlightAsset:)])
        [self.picker.delegate assetsPickerController:self.picker didUnhighlightAsset:asset];
}


@end