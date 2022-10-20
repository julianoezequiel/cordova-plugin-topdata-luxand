//
//  based on ColorTrackingViewController.h
//  from ColorTracking application
//  The source code for this application is available under a BSD license.
//  See ColorTrackingLicense.txt for details.
//  Created by Brad Larson on 10/7/2010.
//  Modified by Anton Malyshev on 6/21/2013.
//

#import <UIKit/UIKit.h>
#import "RecognitionCamera.h"
#import "RecognitionGLView.h"
#include "LuxandFaceSDK.h"

#define MAX_FACES 5

#define MAX_NAME_LEN 1024
@class LuxandProcessor;

FOUNDATION_EXPORT int const ALREADY_REGISTERED;
FOUNDATION_EXPORT int const REGISTERED;
FOUNDATION_EXPORT int const NOT_REGISTERED ;
FOUNDATION_EXPORT int const RECOGNIZED;
FOUNDATION_EXPORT int const NOT_RECOGNIZED;

typedef struct {
    unsigned char * buffer;
    int width, height, scanline;
    float ratio;
} DetectFaceParams;

typedef struct {
    int x1, x2, y1, y2;
} FaceRectangle;


@interface RecognitionViewController : UIViewController <RecognitionCameraDelegate>
{
    RecognitionCamera * camera;
    UIScreen * screenForDisplay;
    LuxandProcessor* luxandProcessor;

    BOOL isRegister;
    BOOL identified;
    long tryCount;
    long initialTryCount;
    long long startTime;
    
    GLuint directDisplayProgram;
    GLuint videoFrameTexture;
    GLubyte * rawPositionPixels;
    
    NSString * templateResponse;
    NSString * templateRef;

    CALayer * trackingRects[MAX_FACES];
    CATextLayer * nameLabels[MAX_FACES];
    
    NSLock * faceDataLock;
    FaceRectangle faces[MAX_FACES];
    
    long long IDs[MAX_FACES];

    CGPoint currentTouchPoint;
    
    volatile int rotating;
    char videoStarted;
    
    volatile int clearTracker;
    
    // FOR LAYOUT
    UIToolbar * toolbar;
    NSTimer * _labelTimer;
    UILabel * textInfo;
    UILabel * textTime;
    NSTimer * _responseTimer;
}

@property(readonly) RecognitionGLView * glView;
@property(readonly) HTracker tracker;
@property(readwrite) NSString *  templatePath;
@property(readwrite) NSString *  templateInit;
@property(readwrite) volatile int closing;
@property(readonly) volatile int processingImage;

// Initialization and teardown
- (id)initWithProcessor:(UIScreen *)newScreenForDisplay processor:(LuxandProcessor*) processor;

-(bool) compararTemplates: (HImage) imagemRef;
-(bool) getTemplate: (HImage) imagemRef;
-(bool) faceEnquadrada;
-(void) response: (BOOL) error message:(NSString*) message;

// OpenGL ES 2.0 setup methods
- (BOOL)loadVertexShader:(NSString *)vertexShaderName fragmentShader:(NSString *)fragmentShaderName forProgram:(GLuint *)programPointer;
- (BOOL)compileShader:(GLuint *)shader type:(GLenum)type file:(NSString *)file;
- (BOOL)linkProgram:(GLuint)prog;
- (BOOL)validateProgram:(GLuint)prog;

// Device rotating support
- (void)relocateSubviewsForOrientation:(UIInterfaceOrientation)orientation;

// Image processing in FaceSDK
- (void)processImageAsyncWith:(NSData *)args;

@end

