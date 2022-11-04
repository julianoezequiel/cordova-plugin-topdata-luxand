#import "RecognitionViewController.h"
#import "Luxand.h"
#import <QuartzCore/QuartzCore.h>

int const ALREADY_REGISTERED = 1;
int const REGISTERED = 2;
int const NOT_REGISTERED = 3;
int const RECOGNIZED = 4;
int const NOT_RECOGNIZED = 5;

NSString * const ENQUADRE_ROSTO = @"ENQUADRE O ROSTO PARA O RECONHECIMENTO";
NSString * const SUCESSO_RECONHECIMENTO = @"BIOMETRIA FACIAL RECONHECIDA";
NSString * const FALHA_RECONHECIMENTO = @"FALHA NO RECONHECIMENTO FACIAL";

NSString * const FRAME_BRANCO = @"frame_branco.png";
NSString * const FRAME_AMARELO = @"frame_amarelo.png";
NSString * const FRAME_VERDE = @"frame_verde.png";

// GL attribute index.
enum {
    ATTRIB_VERTEX,
    ATTRIB_TEXTUREPOSITON,
    NUM_ATTRIBUTES
};

@implementation RecognitionViewController

@synthesize glView = _glView;
@synthesize tracker = _tracker;
@synthesize templatePath = _templatePath;

@synthesize closing = _closing;
@synthesize processingImage = _processingImage;

#pragma mark -
#pragma mark Face frame functions

inline bool PointInRectangle(int point_x, int point_y, int rect_x1, int rect_y1, int rect_x2, int rect_y2)
{
    return (point_x >= rect_x1) && (point_x <= rect_x2) && (point_y >= rect_y1) && (point_y <= rect_y2);
}

int GetFaceFrame(const FSDK_Features * Features, int * x1, int * y1, int * x2, int * y2)
{
    if (!Features || !x1 || !y1 || !x2 || !y2)
        return FSDKE_INVALID_ARGUMENT;
    
    float u1 = (float)(*Features)[0].x;
    float v1 = (float)(*Features)[0].y;
    float u2 = (float)(*Features)[1].x;
    float v2 = (float)(*Features)[1].y;
    float xc = (u1 + u2) / 2;
    float yc = (v1 + v2) / 2;
    int w = (int)pow((u2 - u1) * (u2 - u1) + (v2 - v1) * (v2 - v1), 0.5f);
    
    *x1 = (int)(xc - w * 1.6 * 0.9);
    *y1 = (int)(yc - w * 1.1 * 0.9);
    *x2 = (int)(xc + w * 1.6 * 0.9);
    *y2 = (int)(yc + w * 2.1 * 0.9);
    if (*x2 - *x1 > *y2 - *y1) {
        *x2 = *x1 + *y2 - *y1;
    } else {
        *y2 = *y1 + *x2 - *x1;
    }
    return 0;
}

- (void)resetTrackerParameters
{
    int errpos = 0;
    
    FSDK_SetTrackerMultipleParameters(_tracker,
            "DetectLiveness=true;SmoothAttributeLiveness=true;AttributeLivenessSmoothingAlpha=1;LivenessFramesCount=5;", &errpos);
#if defined(DEBUG)
    if (errpos)
        NSLog(@"FSDK_SetTrackerMultipleParameters returned errpos = %d", errpos);
#endif
    
    FSDK_SetTrackerMultipleParameters(_tracker, "ContinuousVideoFeed=true;FacialFeatureJitterSuppression=0;RecognitionPrecision=1;Threshold=0.996;Threshold2=0.9995;ThresholdFeed=0.97;MemoryLimit=2000;HandleArbitraryRotations=false;DetermineFaceRotationAngle=false;InternalResizeWidth=70;FaceDetectionThreshold=5;", &errpos);
#if defined(DEBUG)
    if (errpos)
        NSLog(@"FSDK_SetTrackerMultipleParameters returned errpos = %d", errpos);
#endif
    
    FSDK_SetTrackerMultipleParameters(_tracker, "DetectAge=true;DetectGender=true;DetectExpression=true", &errpos);
#if defined(DEBUG)
    if (errpos)
        NSLog(@"FSDK_SetTrackerMultipleParameters returned errpos = %d", errpos);
#endif
    
    FSDK_SetTrackerMultipleParameters(_tracker, "AttributeExpressionSmileSmoothingSpatial=0.5;AttributeExpressionSmileSmoothingTemporal=10;", &errpos);
#if defined(DEBUG)
    if (errpos)
        NSLog(@"FSDK_SetTrackerMultipleParameters returned errpos = %d", errpos);
#endif
}

#pragma mark -
#pragma mark Init do RecognitionViewController: inicializando o rastreador de rosto (Tracker)

- (id)initWithProcessor:(UIScreen *)newScreenForDisplay processor:(LuxandProcessor*) processor
{
    _closing = 0;
    luxandProcessor = processor;
    isRegister = processor.isRegister;
    identified = false;
    tryCount = 0;
    initialTryCount = processor.tryCount;
    faceDataLock = [[NSLock alloc] init];
    
    templateResponse = @"";
    templateRef = processor.templateInit;
    
    _templatePath = processor.templatePath;
#if defined(DEBUG)
    NSLog(@"using templatePath: %s", [_templatePath UTF8String]);
#endif
    if ((self = [super initWithNibName:nil bundle:nil])) {
        if (FSDKE_OK != FSDK_LoadTrackerMemoryFromFile(&_tracker, [_templatePath UTF8String])){
            FSDK_CreateTracker(&_tracker);
            NSLog(@"New Tracker");
        }
        
        [self resetTrackerParameters];
        
        screenForDisplay = newScreenForDisplay;
        
        _processingImage = NO;
        rotating = NO;
        videoStarted = 0;
        clearTracker = NO;

        memset(faces, 0, sizeof(FaceRectangle)*MAX_FACES);
    }
    return self;
}

//init view, glview and camera
- (void)loadView
{
    CGRect mainScreenFrame = [[UIScreen mainScreen] bounds];
    
    UIView *primaryView = [[UIView alloc] initWithFrame:mainScreenFrame];
    primaryView.backgroundColor =[UIColor darkGrayColor];
    
    self.view = primaryView;
    primaryView = nil; //now self is responsible for the view

    _glView = [[RecognitionGLView alloc] initWithFrame:CGRectMake(0.0f, 0.0f, mainScreenFrame.size.width, mainScreenFrame.size.height)];
    //_glView will be re-initialized in (void)drawFrame with proper size

    [self.view addSubview: _glView];
    
    // FRAME FIXO
    CGFloat width = 230;
    CGFloat height = 250;
    CGFloat x = (mainScreenFrame.size.width - width) * 0.5f;
    CGFloat y = (mainScreenFrame.size.height - height) * 0.2f;
    
    imageFrame = [[UIImageView alloc] initWithFrame:CGRectMake(x, y, width, height)];
    imageFrame.image = [UIImage imageNamed: FRAME_BRANCO];
    // optional:
    // [imageHolder sizeToFit];
    [self.view addSubview: imageFrame];
    
    /*CGRect frame = CGRectMake(x, y, width, height);
    
    UIView *frameView = [[UIView alloc] initWithFrame: frame];
    
    frameView.backgroundColor = [UIColor clearColor];
    frameView.layer.borderColor = [[UIColor blueColor] CGColor];
    frameView.layer.borderWidth = 3.0;
    
    [self.view addSubview: frameView];*/
    

    
    // CONFIGURANDO BARRA NA PARTE SUPERIOR DA TELA
    /*UIToolbar * toolbarHeader = [UIToolbar new];
    toolbarHeader.barStyle = UIBarStyleBlack;
    toolbarHeader.backgroundColor =[UIColor colorWithDisplayP3Red:39 green:39 blue:39 alpha:1];

    CGRect mainViewBounds = self.view.bounds;
    
    [toolbarHeader sizeToFit];
    [toolbarHeader setFrame:CGRectMake(0, 0, CGRectGetWidth(mainViewBounds), 20)];
    
    [self.view addSubview: toolbarHeader];*/
    
    // CONFIGURANDO A BARRA NA PARTE INFERIOR DA TELA
    toolbar = [UIToolbar new];
    toolbar.barStyle = UIBarStyleBlack;

    CGFloat toolbarHeight = 160;
    CGRect mainViewBounds = self.view.bounds;
    
    UIView *myView = [[UIView alloc] initWithFrame:CGRectMake(CGRectGetMinX(mainViewBounds),
                                                              CGRectGetMinY(mainViewBounds) + CGRectGetHeight(mainViewBounds) - (toolbarHeight),
                                                              CGRectGetWidth(mainViewBounds),
                                                              toolbarHeight)];
    myView.backgroundColor = [UIColor darkGrayColor];
    
    UIBarButtonItem * myViewItem = [[UIBarButtonItem alloc] initWithCustomView: myView];
    
    UIBarButtonItem *flexibleSpace =  [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemFlexibleSpace target:nil action:nil];
    
    toolbar.items = [NSArray arrayWithObjects: flexibleSpace, myViewItem, flexibleSpace, nil];
    
    [toolbar sizeToFit];
    [toolbar setFrame:CGRectMake(CGRectGetMinX(mainViewBounds),
                                 CGRectGetMinY(mainViewBounds) + CGRectGetHeight(mainViewBounds) - (toolbarHeight),
                                 CGRectGetWidth(mainViewBounds),
                                 toolbarHeight)];
    
    [self.view addSubview:toolbar];

    // CONFIGURANDO RELÓGIO
    textTime = [[UILabel alloc] initWithFrame: CGRectMake(0, 0, 0, 0)];
    textTime.backgroundColor = [UIColor clearColor];
    
    NSDateFormatter * dateFormatter = [[NSDateFormatter alloc] init];
    [dateFormatter setDateFormat: @"HH:mm:ss"];
    NSString * dateString = [dateFormatter stringFromDate:[NSDate date]];

    textTime.text = dateString;
    
    textTime.textAlignment = NSTextAlignmentCenter;
    [textTime setFont:[UIFont boldSystemFontOfSize: 18]];
    textTime.numberOfLines = 1;
    CGSize maximumLabelSize = CGSizeMake(textTime.frame.size.width, CGFLOAT_MAX);
    CGSize expectSize = [textTime sizeThatFits:maximumLabelSize];
    textTime.frame = CGRectMake(myView.frame.size.width / 2 - 35, textTime.frame.origin.y, expectSize.width + 5, expectSize.height + 50);
    
    [myView addSubview: textTime];
    
    _labelTimer = [NSTimer scheduledTimerWithTimeInterval: 1.0 repeats:YES block:^(NSTimer *timer) {
        self -> textTime.text = [dateFormatter stringFromDate:[NSDate date]];
    }];
    
    // CONFIGURANDO TEXTO DE INFORMAÇĀO
    textInfo = [[UILabel alloc] initWithFrame: CGRectMake(0, 0, 0, 0)];
    textInfo.backgroundColor = [UIColor clearColor];
    textInfo.text = ENQUADRE_ROSTO;
    textInfo.textAlignment = NSTextAlignmentCenter;
    [textInfo setFont:[UIFont boldSystemFontOfSize: 15]];
    textInfo.numberOfLines = 1;
    maximumLabelSize = CGSizeMake(textInfo.frame.size.width, CGFLOAT_MAX);
    expectSize = [textInfo sizeThatFits:maximumLabelSize];
    textInfo.frame = CGRectMake(textInfo.frame.origin.x + 9, textInfo.frame.origin.y, expectSize.width, expectSize.height + 170);
    
    [myView addSubview: textInfo];
    
    [self loadVertexShader:@"DirectDisplayShader" fragmentShader:@"DirectDisplayShader" forProgram:&directDisplayProgram];
     
    // Desenhando frames nos rostos detectados
    /*for (int i=0; i<MAX_FACES; ++i) {
        trackingRects[i] = [[CALayer alloc] init];
        trackingRects[i].bounds = CGRectMake(0.0f, 0.0f, 0.0f, 0.0f);
        trackingRects[i].cornerRadius = 0.0f;
        trackingRects[i].borderColor = [[UIColor orangeColor] CGColor];
        trackingRects[i].borderWidth = 2.0f;
        trackingRects[i].position = CGPointMake(100.0f, 100.0f);
        trackingRects[i].opacity = 0.0f;
        trackingRects[i].anchorPoint = CGPointMake(0.0f, 0.0f); //for position to be the top-left corner
        nameLabels[i] = [[CATextLayer alloc] init];
        //[nameLabels[i] setFont:@"Helvetica-Bold"];
        [nameLabels[i] setFontSize:20];
        [nameLabels[i] setFrame:CGRectMake(10.0f, 10.0f, 200.0f, 40.0f)];
        [nameLabels[i] setString:@"Tap to name"];
        [nameLabels[i] setAlignmentMode:kCAAlignmentLeft];
        [nameLabels[i] setForegroundColor:[[UIColor greenColor] CGColor]];
        [nameLabels[i] setAlignmentMode:kCAAlignmentCenter];
        //[trackingRects[i] addSublayer:nameLabels[i]];
        //nameLabels[i]  = nil;
    }*/
    
    // Disable animations for move and resize (otherwise trackingRect will jump)
    for (int i=0; i<MAX_FACES; ++i) {
        NSMutableDictionary * newActions = [[NSMutableDictionary alloc] initWithObjectsAndKeys:[NSNull null], @"position", [NSNull null], @"bounds", nil];
        trackingRects[i].actions = newActions;
        newActions = nil;
    }
    for (int i=0; i<MAX_FACES; ++i) {
        NSMutableDictionary * newActions = [[NSMutableDictionary alloc] initWithObjectsAndKeys:[NSNull null], @"position", [NSNull null], @"bounds", nil];
        nameLabels[i].actions = newActions;
        newActions = nil;
    }
    
    for (int i=0; i<MAX_FACES; ++i) {
        [_glView.layer addSublayer:trackingRects[i]];
    }
    
    camera = [[RecognitionCamera alloc] init];
    camera.delegate = self; //we want to receive processNewCameraFrame messages
    [self cameraHasConnected]; //the method doesn't perform any work now
    
    // AJUSTE DE POSIÇĀO CAMERA
    UIInterfaceOrientation orientation = [UIApplication sharedApplication].statusBarOrientation;
    [self relocateSubviewsForOrientation:orientation];
}

- (void)dealloc
{
    for (int i=0; i<MAX_FACES; ++i) {
        trackingRects[i] = nil;
    }
    camera = nil;
    faceDataLock = nil;
    faceDataLock = NULL;
    
    // Make sure it is stopped
    [_labelTimer invalidate];
}

- (void)viewDidDisappear:(BOOL)animated {
    
    // Stop the timer when we leave
    [_labelTimer invalidate];
    _labelTimer = nil;
}


#pragma mark -
#pragma mark OpenGL ES 2.0 rendering

- (void)drawFrame // called by processNewCameraFrame
{
    /*
     // mirrored square
     static const GLfloat squareVertices[] = {
     1.0f, -1.0f,
     -1.0f, -1.0f,
     1.0f,  1.0f,
     -1.0f,  1.0f,
     };
     */
    
    // standart square
    static const GLfloat squareVertices[] = {
        -1.0f, -1.0f,
        1.0f, -1.0f,
        -1.0f,  1.0f,
        1.0f,  1.0f,
    };
    
    /*
     // mirrored texture (was used with standart square originally, result - mirrored image)
     static const GLfloat textureVertices[] = {
     1.0f, 1.0f,
     1.0f, 0.0f,
     0.0f,  1.0f,
     0.0f,  0.0f,
     };
     */
    
    //OLD, OK WHEN NOT CHANGING ORIENTATION
    // standart texture
    /*
     static const GLfloat textureVertices[] = {
     1.0f, 0.0f,
     1.0f, 1.0f,
     0.0f,  0.0f,
     0.0f,  1.0f,
     };
     */
    
    
    // Reinitialize GLView and Toolbar when orientation changed
    
    static UIInterfaceOrientation old_orientation = (UIInterfaceOrientation)0;
    UIInterfaceOrientation orientation = [UIApplication sharedApplication].statusBarOrientation;
    if (orientation != old_orientation) {
        old_orientation = orientation;
        
        [self relocateSubviewsForOrientation:orientation];
    }
    
    // Rotate the texture (image from camera) accordingly to current orientation
    
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, videoFrameTexture);
    glVertexAttribPointer(ATTRIB_VERTEX, 2, GL_FLOAT, 0, 0, squareVertices);
    glEnableVertexAttribArray(ATTRIB_VERTEX);
    if (orientation == 0 || orientation == UIInterfaceOrientationPortrait) {
        GLfloat textureVertices[] = {
            1.0f, 0.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            0.0f, 1.0f,
        };
        glVertexAttribPointer(ATTRIB_TEXTUREPOSITON, 2, GL_FLOAT, 0, 0, textureVertices);
    } else if(orientation == UIInterfaceOrientationPortraitUpsideDown) {
        GLfloat textureVertices[] = {
            0.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 1.0f,
            1.0f, 0.0f,
        };
        glVertexAttribPointer(ATTRIB_TEXTUREPOSITON, 2, GL_FLOAT, 0, 0, textureVertices);
    } else if(orientation == UIInterfaceOrientationLandscapeLeft) {
        GLfloat textureVertices[] = {
            1.0f, 1.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 0.0f,
        };
        glVertexAttribPointer(ATTRIB_TEXTUREPOSITON, 2, GL_FLOAT, 0, 0, textureVertices);
    } else if(orientation == UIInterfaceOrientationLandscapeRight) {
        GLfloat textureVertices[] = {
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
        };
        glVertexAttribPointer(ATTRIB_TEXTUREPOSITON, 2, GL_FLOAT, 0, 0, textureVertices);
    }
    glEnableVertexAttribArray(ATTRIB_TEXTUREPOSITON);
    
    //glDrawArrays(GL_TRIANGLE_STRIP, 0, 4); //not needed
    
    // Setting bounds and position of trackingRect using data received from FSDK_DetectFace
    // need to disable animations because we can show incorrect (old) name for a moment in result
    [CATransaction begin];
    [CATransaction setDisableActions: YES];
    
    [faceDataLock lock];
    for (int i=0; i<MAX_FACES; ++i) {
        if (faces[i].x2) { // have face
            //[nameLabels[i] setFrame:CGRectMake(10.0f, faces[i].y2 - faces[i].y1 + 10.0f, faces[i].x2-faces[i].x1-20.0f, 40.0f)];
            
            trackingRects[i].position = CGPointMake(faces[i].x1, faces[i].y1);
            trackingRects[i].bounds = CGRectMake(0.0f, 0.0f, faces[i].x2-faces[i].x1, faces[i].y2 - faces[i].y1);
            trackingRects[i].opacity = 1.0f;
        } else { // no face
            trackingRects[i].opacity = 0.0f;
        }
    }
    
    [CATransaction commit];
    [faceDataLock unlock];
    
    
    [_glView setDisplayFramebuffer];
    glUseProgram(directDisplayProgram);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    [_glView presentFramebuffer];
    
    videoStarted = 1;
}



#pragma mark -
#pragma mark OpenGL ES 2.0 setup methods

- (BOOL)loadVertexShader:(NSString *)vertexShaderName fragmentShader:(NSString *)fragmentShaderName forProgram:(GLuint *)programPointer
{
    GLuint vertexShader, fragShader;
    NSString *vertShaderPathname, *fragShaderPathname;
    
    // Create shader program.
    *programPointer = glCreateProgram();
    
    // Create and compile vertex shader.
    vertShaderPathname = [[NSBundle mainBundle] pathForResource:vertexShaderName ofType:@"vsh"];
    NSLog(@"vertShaderPathname: %@", vertShaderPathname);
    if (![self compileShader:&vertexShader type:GL_VERTEX_SHADER file:vertShaderPathname]) {
#if defined(DEBUG)
        NSLog(@"Failed to compile vertex shader");
#endif
        return FALSE;
    }
    
    // Create and compile fragment shader.
    fragShaderPathname = [[NSBundle mainBundle] pathForResource:fragmentShaderName ofType:@"fsh"];
    if (![self compileShader:&fragShader type:GL_FRAGMENT_SHADER file:fragShaderPathname]) {
#if defined(DEBUG)
        NSLog(@"Failed to compile fragment shader");
#endif
        return FALSE;
    }
    
    // Attach vertex shader to program.
    glAttachShader(*programPointer, vertexShader);
    
    // Attach fragment shader to program.
    glAttachShader(*programPointer, fragShader);
    
    // Bind attribute locations.
    // This needs to be done prior to linking.
    glBindAttribLocation(*programPointer, ATTRIB_VERTEX, "position");
    glBindAttribLocation(*programPointer, ATTRIB_TEXTUREPOSITON, "inputTextureCoordinate");
    
    // Link program.
    if (![self linkProgram:*programPointer]) {
#if defined(DEBUG)
        NSLog(@"Failed to link program: %d", *programPointer);
#endif
        // cleaning up
        if (vertexShader) {
            glDeleteShader(vertexShader);
            vertexShader = 0;
        }
        if (fragShader) {
            glDeleteShader(fragShader);
            fragShader = 0;
        }
        if (*programPointer) {
            glDeleteProgram(*programPointer);
            *programPointer = 0;
        }
        return FALSE;
    }
    
    // Release vertex and fragment shaders.
    if (vertexShader) {
        glDeleteShader(vertexShader);
    }
    if (fragShader) {
        glDeleteShader(fragShader);
    }
    return TRUE;
}

- (BOOL)compileShader:(GLuint *)shader type:(GLenum)type file:(NSString *)file
{
    const GLchar * source = (GLchar *)[[NSString stringWithContentsOfFile:file encoding:NSUTF8StringEncoding error:nil] UTF8String];
    if (!source) {
#if defined(DEBUG)
        NSLog(@"Failed to load vertex shader");
#endif
        return FALSE;
    }
    *shader = glCreateShader(type);
    glShaderSource(*shader, 1, &source, NULL);
    glCompileShader(*shader);
#if defined(DEBUG)
    GLint logLength;
    glGetShaderiv(*shader, GL_INFO_LOG_LENGTH, &logLength);
    if (logLength > 0) {
        GLchar *log = (GLchar *)malloc(logLength);
        glGetShaderInfoLog(*shader, logLength, &logLength, log);
        NSLog(@"Shader compile log:\n%s", log);
        free(log);
    }
#endif
    GLint status;
    glGetShaderiv(*shader, GL_COMPILE_STATUS, &status);
    if (status == 0) {
        glDeleteShader(*shader);
        return FALSE;
    }
    return TRUE;
}

- (BOOL)linkProgram:(GLuint)prog
{
    glLinkProgram(prog);
#if defined(DEBUG)
    GLint logLength;
    glGetProgramiv(prog, GL_INFO_LOG_LENGTH, &logLength);
    if (logLength > 0) {
        GLchar *log = (GLchar *)malloc(logLength);
        glGetProgramInfoLog(prog, logLength, &logLength, log);
        NSLog(@"Program link log:\n%s", log);
        free(log);
    }
#endif
    GLint status;
    glGetProgramiv(prog, GL_LINK_STATUS, &status);
    if (status == 0)
        return FALSE;
    return TRUE;
}

- (BOOL)validateProgram:(GLuint)prog
{
    glValidateProgram(prog);
#if defined(DEBUG)
    GLint logLength;
    glGetProgramiv(prog, GL_INFO_LOG_LENGTH, &logLength);
    if (logLength > 0) {
        GLchar *log = (GLchar *)malloc(logLength);
        glGetProgramInfoLog(prog, logLength, &logLength, log);
        NSLog(@"Program validate log:\n%s", log);
        free(log);
    }
#endif
    GLint status;
    glGetProgramiv(prog, GL_VALIDATE_STATUS, &status);
    if (status == 0)
        return FALSE;
    return TRUE;
}



#pragma mark -
#pragma mark Métodos RecognitionCameraDelegate: obtenha a imagem da câmera e processe-a

- (void)cameraHasConnected
{
#if defined(DEBUG)
    NSLog(@"Connected to camera");
#endif
}

- (void)processNewCameraFrame:(CVImageBufferRef)cameraFrame
{
    if (rotating)
        return; //not updating GLView on rotating animation (it looks ugly)
    
    // for screenshot
    //CGSize size = [image_for_screenshot size];
    //cameraFrame = (CVPixelBufferRef)[self pixelBufferFromCGImage:[image_for_screenshot CGImage] size:size];
    
    
    CVPixelBufferLockBaseAddress(cameraFrame, 0);
    int bufferHeight = (int)CVPixelBufferGetHeight(cameraFrame);
    int bufferWidth = (int)CVPixelBufferGetWidth(cameraFrame);
    
    // Create a new texture from the camera frame data, draw it (calling drawFrame)
    glGenTextures(1, &videoFrameTexture);
    glBindTexture(GL_TEXTURE_2D, videoFrameTexture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    
    // This is necessary for non-power-of-two textures
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    
    // Using BGRA extension to pull in video frame data directly
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, bufferWidth, bufferHeight, 0, GL_BGRA, GL_UNSIGNED_BYTE, CVPixelBufferGetBaseAddress(cameraFrame));
    [self drawFrame];
    
    if (_processingImage == NO) {
        if (_closing) return;
        _processingImage = YES;
        
        // Copy camera frame to buffer
        
        int scanline = (int)CVPixelBufferGetBytesPerRow(cameraFrame);
        unsigned char * buffer = (unsigned char *)malloc(scanline * bufferHeight);
        if (buffer) {
            memcpy(buffer, CVPixelBufferGetBaseAddress(cameraFrame), scanline * bufferHeight);
        } else {
            _processingImage = NO;
            glDeleteTextures(1, &videoFrameTexture);
            CVPixelBufferUnlockBaseAddress(cameraFrame, 0);
            return;
        }
        
        // Execute face detection and recognition asynchronously
        
        DetectFaceParams args;
        args.width = bufferWidth;
        args.height = bufferHeight;
        args.scanline = scanline;
        args.buffer = buffer;
        
        UIInterfaceOrientation orientation = [UIApplication sharedApplication].statusBarOrientation;
        if (orientation == 0 || orientation == UIInterfaceOrientationPortrait || orientation == UIInterfaceOrientationPortraitUpsideDown) {
            //args.ratio = (float)self.view.bounds.size.height/(float)bufferWidth;
            //using _glView size proportional to video size:
            args.ratio = (float)self.view.bounds.size.width/(float)bufferHeight;
        } else {
            //args.ratio = (float)self.view.bounds.size.width/(float)bufferWidth;
            //using _glView size proportional to video size:
            args.ratio = (float)self.view.bounds.size.height/(float)bufferHeight;
        }
        NSData * argsobj = [NSData dataWithBytes:&args length:sizeof(DetectFaceParams)];
        // will free (buffer) inside
        [self performSelectorInBackground:@selector(processImageAsyncWith:) withObject:argsobj];
    }
    
    glDeleteTextures(1, &videoFrameTexture);
    CVPixelBufferUnlockBaseAddress(cameraFrame, 0);
}

#pragma mark -
#pragma mark Suporte de rotação do dispositivo

- (CGSize)screenSizeOrientationIndependent {
    CGSize screenSize = [UIScreen mainScreen].bounds.size;
    return CGSizeMake(MIN(screenSize.width, screenSize.height), MAX(screenSize.width, screenSize.height));
}

- (void)relocateSubviewsForOrientation:(UIInterfaceOrientation)orientation
{
    [_glView destroyFramebuffer];
    [_glView removeFromSuperview]; //XXX: does not call [_glView release] immediately on iOS9!

    //CGRect applicationFrame = [screenForDisplay applicationFrame];
    CGSize applicationFrame = [self screenSizeOrientationIndependent]; //workaround iOS 8 change, that sizes become orientation-dependent
    
    //DEBUG
    //const int video_width = 352;
    //const int video_height = 288;
    
    const int video_width = (int)camera.width; //640;
    const int video_height = (int)camera.height;//480;
    if (orientation == 0 || orientation == UIInterfaceOrientationPortrait || orientation == UIInterfaceOrientationPortraitUpsideDown) {
        //_glView = [[RecognitionGLView alloc] initWithFrame:CGRectMake(0.0f, 0.0f, applicationFrame.width, applicationFrame.height)];
        //using _glView size proportional to video size:
        _glView = [[RecognitionGLView alloc] initWithFrame:CGRectMake(0.0f, 0.0f, applicationFrame.width, applicationFrame.width * (video_width*1.0f/video_height))];
    } else {
        //_glView = [[RecognitionGLView alloc] initWithFrame:CGRectMake(0.0f, 0.0f, applicationFrame.height, applicationFrame.width)];
        //using _glView size proportional to video size:
        _glView = [[RecognitionGLView alloc] initWithFrame:CGRectMake(0.0f, 0.0f, applicationFrame.width * (video_width*1.0f/video_height), applicationFrame.width)];
    }
    [self.view addSubview:_glView];
    //_glView = nil; //now self.view is responsible for the view
    [self loadVertexShader:@"DirectDisplayShader" fragmentShader:@"DirectDisplayShader" forProgram:&directDisplayProgram];
    for (int i=0; i<MAX_FACES; ++i) {
        [_glView.layer addSublayer:trackingRects[i]];
    }
    
    // Toolbar re-alignment
    /*CGFloat toolbarHeight = [toolbar frame].size.height;
    CGRect mainViewBounds = self.view.bounds;
    [toolbar setFrame:CGRectMake(CGRectGetMinX(mainViewBounds),
                                 CGRectGetMinY(mainViewBounds) + CGRectGetHeight(mainViewBounds) - (toolbarHeight),
                                 CGRectGetWidth(mainViewBounds),
                                 toolbarHeight)];
    [toolbar setHidden:NO];*/
    [self.view sendSubviewToBack:_glView];
}
#pragma mark -
#pragma mark Face detection and recognition

- (void)processImageAsyncWith:(NSData *)args
{
    if(startTime<=0) {
        startTime = (long long)([[NSDate date] timeIntervalSince1970] * 1000);
    }
    
    if(luxandProcessor.timeout != -1 && (long long)([[NSDate date] timeIntervalSince1970] * 1000) - startTime > luxandProcessor.timeout) {
        NSLog(@"com.luxand: DETECTION_TIMEOUT");
        [self response: true message: @"DETECTION_TIMEOUT"];
        _closing = true;
        return;
    }
    
    if (_closing) {
        return;
    }

    // Reading buffer parameters
    DetectFaceParams a;
    [args getBytes:&a length:sizeof(DetectFaceParams)];
    unsigned char * buffer = a.buffer;
    int width = a.width;
    int height = a.height;
    int scanline = a.scanline;
    float ratio = a.ratio;
    
    // Converting BGRA to RGBA
    unsigned char * p1line = buffer;
    unsigned char * p2line = buffer+2;
    for (int y=0; y<height; ++y) {
        unsigned char * p1 = p1line;
        unsigned char * p2 = p2line;
        p1line += scanline;
        p2line += scanline;
        for (int x=0; x<width; ++x) {
            unsigned char tmp = *p1;
            *p1 = *p2;
            *p2 = tmp;
            p1 += 4;
            p2 += 4;
        }
    }
    
    HImage image;
    int res = FSDK_LoadImageFromBuffer(&image, buffer, width, height, scanline, FSDK_IMAGE_COLOR_32BIT);
    free(buffer);
    if (res != FSDKE_OK) {
#if defined(DEBUG)
        NSLog(@"FSDK_LoadImageFromBuffer failed with %d", res);
#endif
        _processingImage = NO;
        return;
    }
    
    // Rotating image basing on orientation
    HImage derotated_image;
    res = FSDK_CreateEmptyImage(&derotated_image);
    if (res != FSDKE_OK) {
#if defined(DEBUG)
        NSLog(@"FSDK_CreateEmptyImage failed with %d", res);
#endif
        FSDK_FreeImage(image);
        _processingImage = NO;
        return;
    }
    
    __block UIInterfaceOrientation df_orientation = NULL;
    
    dispatch_async(dispatch_get_main_queue(), ^{
        df_orientation = [UIApplication sharedApplication].statusBarOrientation;
    });
    
    //UIInterfaceOrientation df_orientation = [UIApplication sharedApplication].statusBarOrientation;
    
    if (df_orientation == 0 || df_orientation == UIInterfaceOrientationPortrait) {
        res = FSDK_RotateImage90(image, 1, derotated_image);
    } else if (df_orientation == UIInterfaceOrientationPortraitUpsideDown) {
        res = FSDK_RotateImage90(image, -1, derotated_image);
    } else if (df_orientation == UIInterfaceOrientationLandscapeLeft) {
        res = FSDK_RotateImage90(image, 0, derotated_image); //will simply copy image
    } else if (df_orientation == UIInterfaceOrientationLandscapeRight) {
        res = FSDK_RotateImage90(image, 2, derotated_image);
    }
    
    if (res != FSDKE_OK) {
#if defined(DEBUG)
        NSLog(@"FSDK_RotateImage90 failed with %d", res);
#endif
        FSDK_FreeImage(image);
        FSDK_FreeImage(derotated_image);
        _processingImage = NO;
        return;
    }
    
    res = FSDK_MirrorImage(derotated_image, true);
    if (res != FSDKE_OK) {
#if defined(DEBUG)
        NSLog(@"FSDK_MirrorImage failed with %d", res);
#endif
        FSDK_FreeImage(image);
        FSDK_FreeImage(derotated_image);
        _processingImage = NO;
        return;
    }

    // Passing frame to FaceSDK, reading face coordinates and names
    long long count = 0;
    FSDK_FeedFrame(_tracker, 0, derotated_image, &count, IDs, sizeof(IDs));
    
    [faceDataLock lock];
    
    memset(faces, 0, sizeof(FaceRectangle)*MAX_FACES);
    
    for (size_t i = 0; i < (size_t)count; ++i) {
        
        FSDK_Features Eyes;
        FSDK_GetTrackerEyes(_tracker, 0, IDs[i], &Eyes);
        
        GetFaceFrame(&Eyes, &(faces[i].x1), &(faces[i].y1), &(faces[i].x2), &(faces[i].y2));
        faces[i].x1 *= ratio;
        faces[i].x2 *= ratio;
        faces[i].y1 *= ratio;
        faces[i].y2 *= ratio;
        //NSLog(@"w=%d x=%d y=%d", faces[i].x1, faces[i].x2, faces[i].y1);
        
        if (_closing == 0) {
            dispatch_async(dispatch_get_main_queue(), ^{
                [self -> textInfo setText: ENQUADRE_ROSTO];
            });
        }

    }
    [faceDataLock unlock];

    if ([self faceEnquadrada] && count == 1 && _closing == 0) {
        
        dispatch_async(dispatch_get_main_queue(), ^{
            [self -> imageFrame setImage:[UIImage imageNamed: FRAME_AMARELO]];
        });
        
        char * value = (char *) malloc(1024);
        float liveness = 0;

        int ok = FSDK_GetTrackerFacialAttribute(_tracker, 0, IDs[0], "Liveness", value, 1024);

        if (ok == FSDKE_OK) {
            FSDK_GetValueConfidence(value, "Liveness", &liveness);
        }
        
        free(value);
        
        if (liveness > luxandProcessor.livenessParam) {
            NSLog(@"com.luxand: LIVE -> %f", liveness);
            
            if(!isRegister) {
                // COMPARAR FACES
                identified = false;
                
                if(count > 1) {
                    if(tryCount< initialTryCount) {
                        NSLog(@"com.luxand: Múltiplas faces detectadas...");
                    }
                }else if(count == 1 && [self faceEnquadrada]) {
                    
                    // Mark and name faces
                    for(int i=0;i<count; i++) {
                        dispatch_async(dispatch_get_main_queue(), ^{
                            [self -> imageFrame setImage:[UIImage imageNamed: FRAME_AMARELO]];
                        });
                        identified = [self compararTemplates: derotated_image];
                    }
                    
                    if(tryCount <= initialTryCount && identified) {
                        _closing = 1;

                        dispatch_async(dispatch_get_main_queue(), ^{
                            [self -> textInfo setText: SUCESSO_RECONHECIMENTO];
                            [self -> imageFrame setImage:[UIImage imageNamed: FRAME_VERDE]];
                        });
                        
                        NSLog(@"com.luxand: FACE_EQUALS");
                        [self response:false message:@"FACE_EQUALS"];
                        return;
                    }
                    
                    tryCount++;
                    
                    if(tryCount >= initialTryCount) {
                        NSLog(@"com.luxand: FAIL_COMPARE");
                        [self response:true message:@"FAIL_COMPARE"];
                        _closing = 1;
                        return;
                    }
                }
            }else {
                // REGISTRAR FACE
                if(count > 1) {
                    if(tryCount < initialTryCount) {
                        NSLog(@"com.luxand: Múltiplas faces detectadas...");
                    }
                }else if(count == 1 && [self faceEnquadrada]){
                        
                    tryCount++;
                    dispatch_async(dispatch_get_main_queue(), ^{
                        [self -> imageFrame setImage:[UIImage imageNamed: FRAME_AMARELO]];
                    });
                    
                    BOOL ok = [self getTemplate: derotated_image];
                        
                    if (!ok) {
                        NSLog(@"com.luxand: ERROR_GET_TEMPLATE");
                        [self response: true message: @"ERROR_GET_TEMPLATE"];
                        _closing = 1;
                        return;
                    }
                        
                    _closing = 1;
                    
                    dispatch_async(dispatch_get_main_queue(), ^{
                        [self -> textInfo setText: SUCESSO_RECONHECIMENTO];
                        [self -> imageFrame setImage:[UIImage imageNamed: FRAME_VERDE]];
                    });
                    
                    NSLog(@"com.luxand: REGISTERED");
                    [self response: false message: @"REGISTERED"];
                    return;
                }
            }
        } else {
            NSLog(@"com.luxand: FAKE -> %f", liveness);
        }
    } else {
        if (_closing == 0) {
            dispatch_async(dispatch_get_main_queue(), ^{
                [self -> textInfo setText: ENQUADRE_ROSTO];
                [self -> imageFrame setImage: [UIImage imageNamed: FRAME_BRANCO]];
            });
        }
    }
    
    FSDK_FreeImage(image);
    FSDK_FreeImage(derotated_image);
    _processingImage = NO;
}

-(void) response: (BOOL) error message:(NSString*) message {
    
    FSDK_FreeTracker(_tracker);
    
    NSMutableDictionary *ret = [[NSMutableDictionary alloc] initWithObjectsAndKeys:(error ? @"FAIL" :@"SUCCESS"), @"status", nil];
    [ret setObject:@(error) forKey:@"error"];
    [ret setObject:message forKey:@"message"];
    [ret setObject: templateResponse && isRegister ? templateResponse : @""  forKey:@"template"];
    
    NSTimeInterval delayInSeconds = 1.0;
    dispatch_time_t popTime = dispatch_time(DISPATCH_TIME_NOW, (int64_t)(delayInSeconds * NSEC_PER_SEC));
    dispatch_after(popTime, dispatch_get_main_queue(), ^(void){
        [self -> luxandProcessor sendResult:ret];
    });
}

-(bool) compararTemplates: (HImage) imagemRef {

    // Decodifica o template de referência: base64 -> array de char
    FSDK_FaceTemplate * faceTemplateRef = (FSDK_FaceTemplate *) malloc(1040);

    NSData * decodedData = [[NSData alloc] initWithBase64EncodedString: templateRef options: NSDataBase64DecodingIgnoreUnknownCharacters];
    memcpy(faceTemplateRef -> ftemplate, [decodedData bytes], 1040);

    // Busca o template a partir da imagem detectada
    FSDK_FaceTemplate * faceTemplateDetected = (FSDK_FaceTemplate *) malloc(1040);
    FSDK_GetFaceTemplate(imagemRef, faceTemplateDetected);
    
    float similarity = 0;
    FSDK_MatchFaces(faceTemplateDetected, faceTemplateRef, &similarity);
    
    free(faceTemplateRef);
    free(faceTemplateDetected);
    
    // As faces sāo iguais?
    return similarity > luxandProcessor.matchFacesParam;
}

- (bool) getTemplate:(HImage)imagemRef {
    
    // Busca o template a partir da imagem detectada
    FSDK_FaceTemplate * faceTemplate= (FSDK_FaceTemplate *) malloc(1040);
    int ok = FSDK_GetFaceTemplate(imagemRef, faceTemplate);
    
    // Codifica o template da face detectada: array de char -> base64
    templateResponse = [self encondeToBase64: faceTemplate -> ftemplate];
    
    free(faceTemplate);
    
    return ok == FSDKE_OK;
}

- (bool) faceEnquadrada {

    
    // FRAME FIXO
    CGRect mainScreenFrame = [[UIScreen mainScreen] bounds];
    CGFloat width = 230;
    CGFloat height = 250;
    
    int margemAcerto = 40;
    
    float x1 = (mainScreenFrame.size.width - width) * 0.5f;
    float x2 = (mainScreenFrame.size.width * 0.5f) + width;
    
    float y1 = (mainScreenFrame.size.height - height) * 0.2f;
    float y2 = y1 + height;
    
    float leftAcerto = x1 - margemAcerto;
    float topAcerto = y1 - margemAcerto;
    float rightAcerto = x2 + margemAcerto;
    float bottomAcerto = y2 + margemAcerto;

    return faces[0].x1 >= leftAcerto && faces[0].y1 >= topAcerto && faces[0].x2 <= rightAcerto && faces[0].y2 <= bottomAcerto;
    
}

-(NSString *) encondeToBase64: (char *) theData {
    const uint8_t * input = (const uint8_t * ) theData;
    NSInteger length = 1040;

    static char table[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";

    NSMutableData* data = [NSMutableData dataWithLength:((length + 2) / 3) * 4];
    uint8_t* output = (uint8_t*)data.mutableBytes;

    NSInteger i;
    for (i=0; i < length; i += 3) {
        NSInteger value = 0;
        NSInteger j;
        for (j = i; j < (i + 3); j++) {
            value <<= 8;

            if (j < length) {
                value |= (0xFF & input[j]);
            }
        }

        NSInteger theIndex = (i / 3) * 4;
        output[theIndex + 0] =                    table[(value >> 18) & 0x3F];
        output[theIndex + 1] =                    table[(value >> 12) & 0x3F];
        output[theIndex + 2] = (i + 1) < length ? table[(value >> 6)  & 0x3F] : '=';
        output[theIndex + 3] = (i + 2) < length ? table[(value >> 0)  & 0x3F] : '=';
    }

    return [[NSString alloc] initWithData:data encoding:NSASCIIStringEncoding];
}

@end
