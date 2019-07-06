/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tools.descartes.teastore.webui.servlet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import tools.descartes.research.faasteastorelibrary.interfaces.cartitem.CartItem;
import tools.descartes.research.faasteastorelibrary.interfaces.image.ExistingImage;
import tools.descartes.research.faasteastorelibrary.interfaces.image.size.ImageSize;
import tools.descartes.research.faasteastorelibrary.interfaces.image.size.ImageSizePreset;
import tools.descartes.research.faasteastorelibrary.interfaces.persistence.CategoryEntity;
import tools.descartes.research.faasteastorelibrary.interfaces.persistence.UserEntity;
import tools.descartes.research.faasteastorelibrary.requests.category.GetAllCategoriesRequest;
import tools.descartes.research.faasteastorelibrary.requests.image.GetWebImageRequest;
import tools.descartes.teastore.entities.Category;
import tools.descartes.teastore.entities.message.SessionBlob;
import tools.descartes.teastore.registryclient.Service;
import tools.descartes.teastore.registryclient.loadbalancers.LoadBalancerTimeoutException;
import tools.descartes.teastore.registryclient.util.NotFoundException;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Abstract servlet for the webUI.
 *
 * @author Andre Bauer
 * @author Simon Eismann
 */
public abstract class AbstractUIServlet extends HttpServlet
{
    private static final long serialVersionUID = 1L;
    /**
     * Text for message cookie.
     */
    protected static final String MESSAGECOOKIE = "teastoreMessageCookie";
    /**
     * Text for error message cookie.
     */
    protected static final String ERRORMESSAGECOOKIE = "teastoreErrorMessageCookie";
    /**
     * Text for successful login.
     */
    protected static final String SUCESSLOGIN = "You are logged in!";
    /**
     * Text for logout.
     */
    protected static final String SUCESSLOGOUT = "You are logged out!";
    /**
     * Text for wrong credentials.
     */
    protected static final String WRONGCREDENTIALS = "You used wrong credentials!";
    /**
     * Text for number products cookie.
     */
    protected static final String PRODUCTCOOKIE = "teastorenumberProductsCookie";
    /**
     * Text for session blob.
     */
    protected static final String BLOB = "sessionBlob";
    /**
     * Text for confirmed order.
     */
    protected static final String ORDERCONFIRMED = "Your order is confirmed!";
    /**
     * Text for updated cart.
     */
    protected static final String CARTUPDATED = "Your cart is updated!";
    /**
     * Text for added product.
     */
    protected static final String ADDPRODUCT = "Product %s is added to cart!";
    /**
     * Text for removed product.
     */
    protected static final String REMOVEPRODUCT = "Product %s is removed from cart!";

    protected String getStoreIcon( )
    {
        ExistingImage storeIcon = ExistingImage.ICON;
        ImageSize iconSize = ImageSizePreset.ICON.getImageSize( );

        return new GetWebImageRequest(
                storeIcon.getFolderName( ),
                storeIcon.getFileName( ),
                iconSize.getWidth( ),
                iconSize.getHeight( ) ).performRequest( ).getParsedResponseBody( );
    }

    protected boolean isLoggedIn( final HttpServletRequest request )
    {
        boolean isLoggedIn = false;

        UserEntity userEntity = getLoggedInUser( request );

        if ( userEntity != null )
        {
            isLoggedIn = true;
        }

        return isLoggedIn;
    }

    /**
     * returns null if the user is not logged in
     *
     * @param request
     * @return
     */
    protected UserEntity getLoggedInUser( final HttpServletRequest request )
    {
        return ( UserEntity ) request.getSession( ).getAttribute( "user" );
    }

    protected List< CategoryEntity > getAllCategories( )
    {
        return new GetAllCategoriesRequest( 0, 10 ).performRequest( ).getParsedResponseBody( );
    }

    protected List< CartItem > getCartItems( final HttpServletRequest request )
    {
        List< CartItem > cartItems = ( List< CartItem > ) request.getSession( ).getAttribute( "cartItems" );

        if ( cartItems == null )
        {
            cartItems = new LinkedList<>( );
        }

        return cartItems;
    }

    /**
     * Try to read the SessionBlob from the cookie. If no SessioBlob exist, a new
     * SessionBlob is created. If the SessionBlob is corrupted, an
     * IlligalStateException is thrown.
     *
     * @param request servlet request
     * @return SessionBlob
     */
    protected SessionBlob getSessionBlob( HttpServletRequest request )
    {
        if ( request.getCookies( ) != null )
        {
            for ( Cookie cook : request.getCookies( ) )
            {
                if ( cook.getName( ).equals( BLOB ) )
                {
                    ObjectMapper o = new ObjectMapper( );
                    try
                    {
                        SessionBlob blob = o.readValue( URLDecoder.decode( cook.getValue( ), "UTF-8" ), SessionBlob
                                .class );
                        if ( blob != null )
                        {
                            return blob;
                        }
                    }
                    catch ( IOException e )
                    {
                        throw new IllegalStateException( "Cookie corrupted!" );
                    }
                }
            }
        }
        return new SessionBlob( );
    }

    /**
     * Saves the SessionBlob as Cookie. Throws an IllegalStateException if the
     * SessionBlob is corrupted.
     *
     * @param blob     session blob
     * @param response servlet response
     */
    protected void saveSessionBlob( SessionBlob blob, HttpServletResponse response )
    {
        ObjectMapper o = new ObjectMapper( );
        try
        {
            Cookie cookie = new Cookie( BLOB, URLEncoder.encode( o.writeValueAsString( blob ), "UTF-8" ) );
            response.addCookie( cookie );
        }
        catch ( JsonProcessingException | UnsupportedEncodingException e )
        {
            throw new IllegalStateException( "Could not save blob!" );
        }
    }

    /**
     * Destroys the SessionBlob. Throws an IllegalStateException if the SessionBlob
     * is corrupted.
     *
     * @param blob     session blob
     * @param response servlet response
     */
    protected void destroySessionBlob( SessionBlob blob, HttpServletResponse response )
    {
        ObjectMapper o = new ObjectMapper( );
        try
        {
            Cookie cookie = new Cookie( BLOB, URLEncoder.encode( o.writeValueAsString( blob ), "UTF-8" ) );
            cookie.setMaxAge( 0 );
            response.addCookie( cookie );
        }
        catch ( JsonProcessingException | UnsupportedEncodingException e )
        {
            throw new IllegalStateException( "Could not destroy blob!" );
        }
    }

    /**
     * Redirects to the target and creates an Cookie.
     *
     * @param target     webtarget
     * @param response   servlet response
     * @param cookiename name of cookie
     * @param value      cookie value
     * @throws IOException cookie exception
     */
    protected void redirect( String target, HttpServletResponse response, String cookiename, String value )
            throws IOException
    {
        if ( !cookiename.equals( "" ) )
        {
            Cookie cookie = new Cookie( cookiename, value.replace( " ", "_" ) );
            response.addCookie( cookie );
        }

        redirect( target, response );
    }

    /**
     * Redirects to the target.
     *
     * @param target   webtarget
     * @param response servlet response
     * @throws IOException redirect exception
     */
    protected void redirect( String target, HttpServletResponse response ) throws IOException
    {
        if ( !target.startsWith( "/" ) )
        {
            target = "/" + target;
        }
        response.sendRedirect( getServletContext( ).getContextPath( ) + target );

    }

    /**
     * Checks if specific cookies exist and save their value as message.
     *
     * @param request  servlet request
     * @param response servlet response
     */
    protected void checkforCookie( HttpServletRequest request, HttpServletResponse response )
    {
        if ( request.getCookies( ) != null )
        {
            for ( Cookie cook : request.getCookies( ) )
            {
                if ( cook.getName( ).equals( MESSAGECOOKIE ) )
                {
                    request.setAttribute( "message", cook.getValue( ).replaceAll( "_", " " ) );
                    cook.setMaxAge( 0 );
                    response.addCookie( cook );
                }
                else if ( cook.getName( ).equals( PRODUCTCOOKIE ) )
                {
                    request.setAttribute( "numberProducts", cook.getValue( ) );
                }
                else if ( cook.getName( ).equals( ERRORMESSAGECOOKIE ) )
                {
                    request.setAttribute( "errormessage", cook.getValue( ).replaceAll( "_", " " ) );
                    cook.setMaxAge( 0 );
                    response.addCookie( cook );
                }
            }
        }
    }

    /**
     * @param request  servlet request
     * @param response servlet response
     * @throws IOException      external call exception
     * @throws ServletException exception servlet exception
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
     * response)
     */
    protected void doGet( HttpServletRequest request, HttpServletResponse response )
            throws ServletException, IOException
    {
        try
        {
            handleGETRequest( request, response );
        }
        catch ( LoadBalancerTimeoutException e )
        {
            serveTimoutResponse( request, response, e.getTargetService( ) );
        }
        catch ( NotFoundException e )
        {
            serveNotFoundException( request, response, e );
        }
        catch ( Exception e )
        {
            serveExceptionResponse( request, response, e );
        }

    }

    /**
     * @param request  servlet request
     * @param response servlet response
     * @throws IOException      external call exception
     * @throws ServletException exception servlet exception
     * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse
     * response)
     */
    protected void doPost( HttpServletRequest request, HttpServletResponse response )
            throws ServletException, IOException
    {
        try
        {

            handlePOSTRequest( request, response );
        }
        catch ( LoadBalancerTimeoutException e )
        {
            serveTimoutResponse( request, response, e.getTargetService( ) );
        }
        catch ( NotFoundException e )
        {
            serveNotFoundException( request, response, e );
        }
        catch ( Exception e )
        {
            serveExceptionResponse( request, response, e );
        }
    }

    /**
     * Handles a http POST request internally.
     *
     * @param request  The request.
     * @param response The response to write to.
     * @throws ServletException             ServletException on error.
     * @throws IOException                  IOException on error.
     * @throws LoadBalancerTimeoutException Exception on timeouts and load balancer errors.
     */
    protected void handlePOSTRequest( HttpServletRequest request, HttpServletResponse response )
            throws ServletException, IOException, LoadBalancerTimeoutException
    {
        handleGETRequest( request, response );
    }

    /**
     * Handles a http GET request internally.
     *
     * @param request  The request.
     * @param response The response to write to.
     * @throws ServletException             ServletException on error.
     * @throws IOException                  IOException on error.
     * @throws LoadBalancerTimeoutException Exception on timeouts and load balancer errors.
     */
    protected abstract void handleGETRequest( HttpServletRequest request, HttpServletResponse response )
            throws ServletException, IOException, LoadBalancerTimeoutException;

    private void serveTimoutResponse( HttpServletRequest request, HttpServletResponse response, Service service )
            throws ServletException, IOException
    {
        response.setStatus( 408 );
        request.setAttribute( "CategoryList", new ArrayList< Category >( ) );
        request.setAttribute( "storeIcon", "" );
        request.setAttribute( "errorImage", "" );
        request.setAttribute( "title", "TeaStore Timeout" );
        request.setAttribute( "messagetitle", "408: Timout waiting for Service: " + service.getServiceName( ) );
        request.setAttribute( "messageparagraph", "WebUI got a timeout waiting for service \"" + service
                .getServiceName( )
                + "\" to respond. Note the that service may itself have been waiting for another service." );
        request.setAttribute( "login", false );
        request.getRequestDispatcher( "WEB-INF/pages/error.jsp" ).forward( request, response );
    }

    private void serveExceptionResponse( HttpServletRequest request, HttpServletResponse response, Exception e )
            throws ServletException, IOException
    {
        StringWriter sw = new StringWriter( );
        e.printStackTrace( new PrintWriter( sw ) );
        String exceptionAsString = sw.toString( );
        response.setStatus( 500 );
        request.setAttribute( "CategoryList", new ArrayList< Category >( ) );
        request.setAttribute( "storeIcon", "" );
        request.setAttribute( "errorImage", "" );
        request.setAttribute( "title", "TeaStore Timeout" );
        request.setAttribute( "messagetitle", "500: Internal Exception: " + e.getMessage( ) );
        request.setAttribute( "messageparagraph", exceptionAsString );
        request.setAttribute( "login", false );
        request.getRequestDispatcher( "WEB-INF/pages/error.jsp" ).forward( request, response );
    }

    private void serveNotFoundException( HttpServletRequest request, HttpServletResponse response, Exception e )
            throws ServletException, IOException
    {
        StringWriter sw = new StringWriter( );
        e.printStackTrace( new PrintWriter( sw ) );
        String exceptionAsString = sw.toString( );
        response.setStatus( 404 );
        request.setAttribute( "CategoryList", new ArrayList< Category >( ) );
        request.setAttribute( "storeIcon", "" );
        request.setAttribute( "errorImage", "" );
        request.setAttribute( "title", "TeaStore Timeout" );
        request.setAttribute( "messagetitle", "404: Not Found Exception: " + e.getMessage( ) );
        request.setAttribute( "messageparagraph", exceptionAsString );
        request.setAttribute( "login", false );
        request.getRequestDispatcher( "WEB-INF/pages/error.jsp" ).forward( request, response );
    }
}