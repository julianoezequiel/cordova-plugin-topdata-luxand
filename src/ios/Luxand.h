#import <Foundation/Foundation.h>
#import <Cordova/CDVPlugin.h>
#import <UIKit/UIKit.h>

@class RecognitionViewController;


@interface Luxand : CDVPlugin {}
    -(void)init: (CDVInvokedUrlCommand*)command;
    -(void)compare: (CDVInvokedUrlCommand*)command;
    -(void)register: (CDVInvokedUrlCommand*)command;
    -(void)clear: (CDVInvokedUrlCommand*)command;
    -(void)clearMemory: (CDVInvokedUrlCommand*)command;

    -(void)sendSuccess:(NSDictionary*)data commandId: (NSString*) callbackId;
    -(void)sendError:(NSDictionary*)data commandId: (NSString*) callbackId;
    -(BOOL)isUsageDescriptionSet;
    -(BOOL)notHasPermission;
    @property (nonatomic, readwrite) long tryCount;
    @property (nonatomic, readwrite) NSString* licence;
    @property (nonatomic, readwrite) NSString* dbName;
    @property (nonatomic, readwrite) char * templatePath;
    @property (nonatomic, readwrite) NSString* templateInit;
    @property (nonatomic, readwrite) float livenessParam;
    @property (nonatomic, readwrite) float matchFacesParam;
    @property (nonatomic, readwrite) LuxandProcessor* processor;
@end


@interface LuxandProcessor : NSObject <UIApplicationDelegate> {
    RecognitionViewController * viewController;
}
@property (nonatomic, retain) Luxand * plugin;
@property (nonatomic, retain) NSString * callback;
@property (nonatomic, retain) UIViewController * parentViewController;
@property (nonatomic, readwrite) long timeout;
@property (nonatomic, readwrite) NSString * licence;
@property (nonatomic, readwrite) long tryCount;
@property (nonatomic, readwrite) BOOL isRegister;
@property (nonatomic, retain) NSString * templatePath;
@property (nonatomic, readwrite) NSString* templateInit;
@property (nonatomic, readwrite) float livenessParam;
@property (nonatomic, readwrite) float matchFacesParam;

- (id)initWithPlugin:(Luxand*)plugin callback:(NSString*)callback parentViewController: (UIViewController*) parentViewController licence : (NSString*) licence timeout: (long) timeout retryCount: (long) retry isRegister:(BOOL) forIdenftifying templatePath: (NSString*) dbPath templateInit: (NSString*) templateRef livenessParam: (float) livenessParam matchFacesParam: (float) matchFacesParam;
- (void) compare;
- (void) register;
-(void) sendResult: (NSDictionary*) data;
@end
